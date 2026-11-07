# Liberate Us 0.2 architecture

## Core rule

The UI must never communicate directly with Librus/Synergia HTTP APIs.

Target flow:

Compose UI
    ↓
ViewModel
    ↓
Repository
    ↓
Room + RemoteDataSource

Room will become the local source of truth.

## Packages

### data/local

Room database, entities and DAO interfaces.

Planned:

- LiberateDatabase
- MessageEntity
- MessageContentEntity
- SyncStateEntity
- MessageDao
- MessageContentDao
- SyncStateDao

### data/remote

Network-facing code.

Planned:

- SessionManager
- MessagesRemoteDataSource

### data/remote/dto

Objects representing raw remote/API responses.

They must not be used directly by the UI.

### data/repository

Coordinates local and remote data.

Planned:

- MessageRepository

The repository decides whether data comes from Room or from the server.

### security

Credential and session persistence.

SecureLoginStore will eventually move behind this layer.

### ui/login

Login UI and LoginViewModel.

### ui/inbox

Inbox UI and InboxViewModel.

Inbox will observe Room using Flow.

### ui/message

Full-message UI and MessageViewModel.

Full message body will use cache-first loading.

### ui/theme

Liberate Us Material theme and branding.

## Message synchronization rules

1. Inbox synchronization downloads metadata only.
2. Background synchronization must never download message bodies.
3. Fetching a full message is allowed only when the user explicitly opens it.
4. A downloaded body is stored locally and reused.
5. Messages are not deleted locally merely because they disappear from the server's latest-page response.
6. The UI reads messages from Room, not directly from HTTP responses.

## Liberate Us 0.2 restriction

Automatic recurring server polling is intentionally disabled.

WorkManager periodic synchronization will return in a later version only after the repository/Room architecture is complete.

## Current migration status

Step 1:
- separate applicationId
- separate installation
- periodic polling disabled

Step 2:
- architecture package skeleton
- Room dependencies
- KSP
- Lifecycle/ViewModel dependencies

No production behavior is changed in Step 2.


## Domain layer

Domain models are independent from Room and remote API models.

Current message domain:

- `InboxItem`
- `MessageContent`
- `Message`

Room entities are converted through explicit mapper functions in:

- `data/mapper/MessageMappers.kt`

Rules:

1. UI should ultimately consume domain models, not Room entities.
2. Remote API DTOs must not leak into UI.
3. `accountId` is a repository/persistence boundary and is not stored in the UI-facing message domain model.
4. Message body and message metadata remain separate.
5. Mapper code validates that cached content belongs to the same account and message before combining it.


## Session layer

Authentication and session operations are now exposed through:

- `SessionManager`

The implementation currently delegates to the proven legacy
`LibrusAuthClient` through:

- `LibrusConnection`
- `LibrusSessionBackend`

The shared `LibrusConnection` is important during migration because
authentication and message requests must use the same CookieJar and
therefore the same `oauth_token`.

UI code must no longer call authentication/session methods directly on
`LibrusAuthClient`.

Temporary Step 5 exception:

- inbox/message HTTP methods still use the shared legacy client;
- these calls move to `MessagesRemoteDataSource` in Step 6.

SessionManager owns these logical operations:

- login
- saved credentials check
- saved username
- restored-session check
- resume saved login
- full relogin from saved credentials
- OAuth token refresh
- logout

No OAuth or cookie behavior was rewritten in Step 5.


## Messages remote layer

Message network operations are isolated behind:

- `MessagesRemoteDataSource`
- `MessagesRemoteBackend`
- `LibrusMessagesBackend`

Transport models:

- `RemoteInboxMessage`
- `RemoteFullMessage`

`MainActivity` no longer accesses `LibrusAuthClient` message methods directly.

Safety rule:

- `getInbox()` uses only the inbox list operation.
- inbox synchronization downloads metadata only.
- inbox synchronization never calls the full-message operation.
- `getMessage()` is the only remote path that downloads message body.
- fetching a full message is treated as an explicit message-open action because it can change server-side read state.

Both `SessionManager` and `LibrusMessagesBackend` still use the same
`LibrusConnection`, so authentication cookies remain shared.

Room is not yet the UI source of truth. That begins with `MessageRepository`.


## Message repository

`MessageRepository` now coordinates:

- `MessagesRemoteDataSource`
- Room message metadata
- Room sync state
- session identity

Current behavior:

- `observeInbox(accountId)` exposes Room metadata as domain `InboxItem` objects.
- `syncInbox(accountId)` downloads metadata only and UPSERTs it into Room.
- messages missing from the latest remote page are not deleted locally.
- existing `firstSeenAt` is preserved.
- technical sync state records last attempt, last success and error.
- `getMessage(accountId, messageId)` performs an explicit full-message fetch and marks local metadata read.

Deliberately deferred:

- UI still does not observe repository/Room yet; this is Step 8.
- full-message content is not cached yet; this is Step 9.
- automatic session refresh/relogin recovery is Step 12.
- baseline/new-message notification semantics are Step 13.


## Step 8 — Room-driven inbox

Inbox metadata now follows one data path:

`server -> MessagesRemoteDataSource -> MessageRepository -> Room`

The visible list follows:

`Room -> MessageRepository.observeInbox() -> Compose`

`MainActivity` no longer consumes `MessagesRemoteDataSource.getInbox()`
directly.

The existing `RemoteInboxMessage` type remains temporarily as a UI projection
only. Its values are produced from Room records, not from the network result.

Full message content remains an explicit remote operation until Step 9.


## Step 10 — true offline access

Local account access and remote session availability are now separate concepts.

At application start:

- saved account data allows immediate entry into the Room-backed UI;
- session restoration runs without blocking access to cached data;
- successful restoration enters online mode;
- temporary/session failures keep the app in local mode;
- CAPTCHA also keeps previously cached account data available;
- explicit `InvalidCredentials` requires login.

Therefore lack of network no longer sends an existing user to the login screen.

Inbox metadata remains Room-driven.

Cached full-message content remains readable without network.

Manual refresh may fail without removing local inbox data.

Opening a message whose body has never been cached still requires network.

`connected` is currently a legacy UI-access flag. `onlineSession` separately
represents live Librus-session availability. These states will move out of
MainActivity during the ViewModel/UI split.


## Step 11 — UI split and presentation ViewModel

Step 11 was completed in two isolated phases.

### Step 11A

Pure Compose UI was extracted from `MainActivity` into:

- `ui/login/LoginScreen.kt`
- `ui/inbox/InboxScreen.kt`
- `ui/message/MessageScreens.kt`
- `ui/components/StatusText.kt`

### Step 11B

Presentation state moved from Compose `remember` storage to
`MainUiViewModel`.

The ViewModel owns:

- login and password fields;
- local account UI access (`connected`);
- live session presentation state (`onlineSession`);
- busy/status;
- inbox projection;
- expanded message;
- notification/deep-link message;
- message loading/error state;
- periodic-toggle presentation state.

`MainActivity` still performs orchestration and side effects.

In particular, Step 11 does NOT change:

- `SessionManager.resumeSavedLogin()`;
- `SessionManager.loginWithSavedCredentials()`;
- manual login semantics;
- repository synchronization;
- Room observation;
- Step 10 offline startup policy.

Central session recovery remains Step 12.


## Step 12 — central session recovery

Session-recovery policy now belongs to `SessionManager`.

Application orchestration no longer chooses directly between:

- existing session;
- `/refreshToken`;
- full login with saved credentials.

Startup recovery order:

1. accept an already restored oauth token;
2. attempt lightweight token refresh;
3. if refresh cleanly fails, attempt login with saved credentials;
4. classify the outcome as ONLINE, LOCAL, or LOGIN_REQUIRED.

If refresh itself throws because the network/server is unavailable, the
manager does not immediately issue a second full-login network sequence.
Existing cached account data remains available in LOCAL mode.

After an authenticated request failure, the existing token is not trusted.
Recovery starts at refresh and may fall back to saved credentials.

`MainActivity` now calls:

- `recoverSavedSession()` at startup;
- `recoverAfterRequestFailure()` for request recovery.

It no longer directly calls `resumeSavedLogin()`,
`loginWithSavedCredentials()`, or `refreshOAuthToken()`.

Manual user login remains explicit and unchanged.

Room schema and message persistence are unchanged.


## Step 13 — Room baseline and new-message detection

`sync_state.baselineInitialized` is now active.

For each account:

1. the first successful inbox metadata synchronization establishes
   the baseline;
2. baseline rows are persisted but are not classified as new;
3. a failed initial synchronization leaves the baseline uninitialized;
4. after baseline creation, only message IDs absent from Room before
   the current synchronization are classified as new;
5. once inserted, the same message ID cannot be classified as new
   by later synchronizations.

`InboxSyncResult` distinguishes:

- `insertedCount` — technical Room inserts;
- `newMessages` / `newMessageCount` — semantic new-message detection;
- `wasBaselineSync` — whether this synchronization established
  the baseline.

Verification:

- unit tests cover baseline, later new-message detection,
  duplicate suppression and failed-first-sync behavior;
- an on-device smoke test used a real in-memory Room database
  and real DAOs;
- baseline sync produced 0 semantic new messages;
- the next sync detected exactly one new ID;
- the following identical sync detected 0 new messages;
- no full message body GET occurred.

The smoke database was isolated in memory and did not modify the
production `liberate_us.db`.

Room schema remains version 1.

Periodic checking remains hard-disabled in Liberate Us 0.2.


## Step 14A — domain models at the presentation boundary

The presentation layer no longer consumes remote transport DTOs.

`MainActivity`, `MainUiViewModel`, `InboxScreen` and
`MessageScreens` now use:

- `InboxItem` for inbox metadata;
- `Message` for opened messages;
- `MessageContent` through `Message.content`.

The Room observer passes `List<InboxItem>` directly to presentation.

The repository full-message path returns `Message` directly to
presentation. `MainActivity` no longer converts the domain model back
into `RemoteFullMessage`.

`RemoteInboxMessage` and `RemoteFullMessage` remain intentionally in
the remote/repository transport boundary.

Network behavior, Room schema, session recovery and Step 13 baseline
semantics are unchanged.

Periodic checking remains disabled.


## Step 14B — legacy periodic message architecture removed

The previous periodic message implementation has been removed from the
active architecture.

Removed:

- direct background use of `LibrusAuthClient`;
- direct background `fetchInbox()` calls;
- the legacy SharedPreferences seen-ID store;
- the previous seen-ID baseline;
- the random periodic scheduling implementation;
- the old background notification path;
- periodic toggle/state from the presentation layer.

A minimal `PeriodicMessageCheckWorker` remains only as a compatibility
shell for WorkManager requests that may have been serialized by an older
development build.

That compatibility worker:

- performs no Librus request;
- performs no repository synchronization;
- creates no notification;
- immediately returns success.

Liberate Us 0.2 therefore has no active periodic polling path.

The canonical new-message mechanism is now:

Room `sync_state.baselineInitialized`
    -> `MessageRepository.syncInbox()`
    -> `InboxSyncResult.newMessages`

A future background implementation must use:

SessionManager
    -> MessageRepository
    -> Room
    -> InboxSyncResult.newMessages
    -> notifications

and must not reintroduce a separate seen-ID baseline.

Room schema remains version 1.


## Step 14C — correctness hardening before local history

Three correctness issues were fixed before extending local history.

### Opening an existing message

Fetching an uncached full body for an existing inbox row no longer
changes that row's historical ordering metadata.

For an existing message, full-message fetch preserves:

- `remoteOrder`;
- `firstSeenAt`;
- `lastSeenAt`.

The remote full response may still refresh descriptive metadata and the
message is marked read.

Opening an old message therefore cannot move it merely because its body
was fetched.

### Coroutine cancellation

`MessageRepository.syncInbox()` now treats `CancellationException` as
coroutine control flow rather than an ordinary synchronization failure.

Cancellation is rethrown immediately and is not written into
`SyncStateEntity.lastError`.

### Local-account availability

`MainActivity` no longer remembers a one-time Boolean describing whether
saved local credentials exist.

The value is evaluated when session recovery is actually requested, so a
successful manual login performed after the initial composition can be
used by later recovery flows.

Regression tests cover message ordering preservation and cancellation
semantics.

Room schema remains version 1.


## Step 15 — local inbox history beyond the latest 10

A live authenticated metadata-only probe established that the current
Librus inbox endpoint exposes older history through a larger `limit`.

Observed results:

- `limit=10` -> 10 unique messages;
- `limit=25` -> 25 unique messages;
- `limit=50` -> 50 unique messages;
- `limit=100` -> 51 unique messages.

The first 10 IDs were the same prefix at every tested limit.

### Normal synchronization

`syncInbox()` remains a 10-row synchronization path.

It remains the only path responsible for:

- initial baseline creation;
- `InboxSyncResult.newMessages`;
- future notification semantics.

### History backfill

`backfillInboxHistory()` is a separate metadata-only path.

It:

- requests up to 100 inbox metadata rows;
- performs UPSERT only;
- preserves `firstSeenAt` for existing rows;
- refreshes current server ordering metadata;
- never fetches full message bodies;
- never changes `SyncStateEntity`;
- never creates notification candidates.

This prevents historical messages discovered during backfill from being
interpreted as newly arrived messages.

### Automatic trigger

After a normal 10-row synchronization, the larger history request runs
only when:

- the normal request returned all 10 rows; and
- local Room history still contains at most 10 rows.

After local history grows beyond 10 rows, subsequent refreshes remain on
the lightweight normal synchronization path.

Room remains the presentation source of truth.

Room schema remains version 1.


## Step 16 — UI polish

Step 16 was part of the original Liberate Us 0.2 plan.

The change is presentation-only. Repository, Room, session recovery, and
synchronization semantics are unchanged.

### Inbox

The inbox now uses a single lazy list as its primary scroll container.

Presentation includes:

- compact Liberate Us header;
- Online / Lokalnie session badge;
- compact account card;
- refresh action;
- local history count;
- unread indicator;
- human-readable dates;
- lazy rendering of the full local message history.

### Dates

Raw Librus timestamps remain unchanged in Room and domain models.

For example:

`2026-10-27T16:56:24`

is displayed as:

`27.10.2026, 16:56`

### Login and direct message screens

Login, full-message, loading, and error screens use a consistent
Material 3 hierarchy.

### Architecture boundaries

Presentation contains no remote DTOs and no direct repository/session/
Librus transport access.

Room schema remains version 1.

Periodic synchronization remains disabled in Liberate Us 0.2.


## Step 17 — logout and account boundaries

Logout and account-switch behavior is now explicit.

### Logout

`SessionManager.logout()` remains responsible for removing:

- authentication cookies;
- encrypted saved credentials.

`MainUiViewModel.resetForLogout()` is the single presentation-state
reset path.

It clears:

- login/password fields;
- connected/online/busy flags;
- status;
- inbox presentation state;
- expanded inline message;
- direct/deep-link message;
- direct-message loading/error state;
- currently opening message ID.

### Local cache policy

Logout intentionally does NOT erase Room history.

Local data remains available for future offline access after the same
account signs in again.

This is safe because all persistent message state is partitioned by
`accountId`:

- `messages`: `(accountId, messageId)`;
- `message_contents`: `(accountId, messageId)`;
- `sync_state`: `accountId`.

Repository and DAO operations require the account explicitly.

### In-flight operations

Manual refresh and message-open operations capture the account that
started them.

A result is applied to presentation state only if that same account is
still active when the operation finishes.

This prevents a request started for account A from updating the UI after
logout or after account B becomes active.

### Regression coverage

A dedicated repository test stores the same `messageId` under two
different accounts and verifies that each account observes only its own
row and sync state.

Room schema remains version 1.

Periodic synchronization remains disabled in Liberate Us 0.2.


## Step 20 — Liberate Us 0.3 background synchronization

Liberate Us 0.3 enables background inbox metadata synchronization.

Architecture:

WorkManager
-> SessionManager
-> MessagesRemoteDataSource
-> MessageRepository
-> Room

Scheduling:

- PeriodicWorkRequest repeat interval: 45 minutes.
- Flex interval: 15 minutes.
- Normal WorkManager execution window: 30–45 minutes.
- Initial delay: 30 minutes.
- Network constraint: CONNECTED.
- Android may defer execution further because of Doze or system battery policy.

Semantics:

- background synchronization downloads inbox metadata only;
- background synchronization never downloads message bodies;
- MessageRepository remains responsible for baseline/new-message detection;
- first successful baseline synchronization creates no notifications;
- notifications are generated only from InboxSyncResult.newMessages;
- failed network/session attempts are not retried immediately, preserving the intended polling cadence;
- SessionManager owns all authentication recovery;
- logout cancels background work and clears this app's message notifications;
- successful manual login schedules background work;
- ONLINE and temporary LOCAL startup recovery states schedule background work;
- LOGIN_REQUIRED cancels background work.

The frozen Liberate Us 0.2.0 snapshot remains external and immutable.
