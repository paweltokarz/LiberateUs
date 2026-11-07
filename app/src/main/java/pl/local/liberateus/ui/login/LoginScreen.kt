package pl.local.liberateus.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import pl.local.liberateus.R
import pl.local.liberateus.ui.components.StatusText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

@Composable
internal fun LoginScreen(
    login: String,
    password: String,
    status: String,
    busy: Boolean,
    onLoginChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLogin: () -> Unit
) {
    var passwordVisible by
        rememberSaveable {
            mutableStateOf(false)
        }


    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal =
                        24.dp,
                    vertical =
                        28.dp
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Spacer(
            Modifier.height(
                20.dp
            )
        )

        Image(
            painter =
                painterResource(
                    id =
                        R.drawable
                            .ic_launcher_foreground
                ),
            contentDescription =
                null,
            modifier =
                Modifier.size(
                    82.dp
                )
        )

        Spacer(
            Modifier.height(
                8.dp
            )
        )

        Text(
            text =
                "Liberate Us",
            style =
                MaterialTheme
                    .typography
                    .headlineLarge,
            fontWeight =
                FontWeight.Bold
        )

        Text(
            text =
                "Wiadomości Librus Synergia",
            style =
                MaterialTheme
                    .typography
                    .bodyLarge,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )

        Spacer(
            Modifier.height(
                32.dp
            )
        )

        Card(
            modifier =
                Modifier.fillMaxWidth(),
            shape =
                RoundedCornerShape(
                    24.dp
                ),
            colors =
                CardDefaults
                    .cardColors(
                        containerColor =
                            MaterialTheme
                                .colorScheme
                                .surfaceVariant
                                .copy(
                                    alpha =
                                        0.45f
                                )
                    )
        ) {

            Column(
                modifier =
                    Modifier.padding(
                        20.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        14.dp
                    )
            ) {

                Text(
                    text =
                        "Logowanie",
                    style =
                        MaterialTheme
                            .typography
                            .titleLarge,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Text(
                    text =
                        "Użyj danych do konta Synergia.",
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )

                OutlinedTextField(
                    value =
                        login,
                    onValueChange =
                        onLoginChanged,
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            "Login Synergia"
                        )
                    },
                    singleLine =
                        true,
                    enabled =
                        !busy
                )

                OutlinedTextField(
                    value =
                        password,
                    onValueChange =
                        onPasswordChanged,
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            "Hasło"
                        )
                    },
                    singleLine =
                        true,
                    enabled =
                        !busy,
                    visualTransformation =
                        if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    trailingIcon = {
                        IconButton(
                            enabled = !busy,
                            onClick = {
                                passwordVisible =
                                    !passwordVisible
                            }
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        id =
                                            if (
                                                passwordVisible
                                            ) {
                                                R.drawable.ic_visibility_off
                                            } else {
                                                R.drawable.ic_visibility
                                            }
                                    ),
                                contentDescription =
                                    if (
                                        passwordVisible
                                    ) {
                                        "Ukryj hasło"
                                    } else {
                                        "Pokaż hasło"
                                    }
                            )
                        }
                    },)

                Button(
                    modifier =
                        Modifier.fillMaxWidth(),
                    enabled =
                        !busy
                            &&
                            login.isNotBlank()
                            &&
                            password.isNotBlank(),
                    onClick =
                        onLogin
                ) {
                    Text(
                        if (
                            busy
                        ) {
                            "Logowanie…"
                        } else {
                            "Zaloguj"
                        }
                    )
                }
            }
        }

        Spacer(
            Modifier.height(
                18.dp
            )
        )

        StatusText(
            status =
                status,
            busy =
                busy
        )
    }
}
