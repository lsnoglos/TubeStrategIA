# Autenticación 0.2.0
La pantalla de entrada usa Google Credential Manager y el cliente web OAuth configurado. La APK se firma con el certificado ya registrado. La identidad solo se usa en la interfaz local y no implica una sesión de servidor verificada. El token no se guarda ni registra.
El permiso drive.appdata se solicitará separadamente al iniciar la sincronización.
La clave de YouTube ya no aparece como requisito de entrada. Se mantiene temporalmente en Mi cuenta > Diagnóstico API para pruebas personales. Para distribución, las consultas públicas deberán pasar por un servicio central con clave protegida, validación de tokens de Google, cuotas y limitación de solicitudes. No incrustar claves ni secretos en el APK. 
Pendiente: conectar y desplegar ese servicio, verificación de token en servidor, persistencia de sesión, almacenamiento Drive y SQLite, y pruebas reales en el teléfono.
