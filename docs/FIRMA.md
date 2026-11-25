# Firma de TubeStrategIA
GitHub Actions compila la variante release sin firmar y firma la APK en una etapa separada usando el almacén privado. Nunca se incluyen las contraseñas ni el JKS en el repositorio o en el artefacto.
Secretos requeridos: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD.
El flujo verifica la firma con apksigner y exige el SHA-1 registrado para el cliente OAuth Android: DD:83:28:E5:92:92:33:D8:4E:A6:8B:54:B4:87:1F:81:A0:25:34:F4.
El artefacto de una ejecución correcta se llama TubeStrategIA-firmada y contiene TubeStrategIA.apk. Si se instaló una versión debug con certificado diferente, Android requerirá desinstalarla antes de instalar esta versión, lo que elimina sus datos locales.
Conservar el JKS original y sus contraseñas fuera del repositorio. No compartir secretos ni incluirlos en logs. Restringir quién puede modificar workflows y la rama principal.
El login de Google y la sincronización de Drive se incorporarán después de verificar esta base firmada.
