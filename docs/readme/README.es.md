<div align="center">

<img src="../images/jiumi-icon.png" alt="Icono de Jiumi" width="80" />

# Jiumi 揪咪

Descubre qué hacen las aplicaciones en primer y segundo plano y decide si conservarlas, restringirlas, deshabilitarlas o desinstalarlas.
<br>No requiere root ni computadora. Los registros se guardan en el teléfono.

**[Descargar el APK](https://github.com/ybblab/Jiumi/releases/latest)** · [Guía](https://jiumi.ybblab.com/en/download/) · [Sitio web](https://jiumi.ybblab.com/en/)

<sub>Android 11 o posterior · ARM64 · APK de unos 4 MB · <a href="../../LICENSE">Apache 2.0</a></sub>

[简体中文](../../README.md) · [English](README.en.md) · [日本語](README.ja.md) · [한국어](README.ko.md) · **Español** · [Deutsch](README.de.md) · [Français](README.fr.md) · [हिन्दी](README.hi.md) · [Bahasa Indonesia](README.id.md) · [Português (Brasil)](README.pt-BR.md) · [Русский](README.ru.md)

</div>

## Vistazo a la aplicación

<p align="center">
  <a href="../images/monitor.en.png"><img src="../images/monitor.en.png" width="196" alt="Actividad de 30 días con mapa de calor" /></a>&nbsp;
  <a href="../images/monitor-week.en.png"><img src="../images/monitor-week.en.png" width="196" alt="Tendencia de 7 días con mapa de calor" /></a>&nbsp;
  <a href="../images/by-app.en.png"><img src="../images/by-app.en.png" width="196" alt="Aplicaciones ordenadas por accesos y actividad en segundo plano" /></a>&nbsp;
  <a href="../images/app-detail.en.png"><img src="../images/app-detail.en.png" width="196" alt="Curva de actividad de una aplicación durante 30 días" /></a>
</p>

<p align="center">
  <a href="../images/timeline.en.png"><img src="../images/timeline.en.png" width="196" alt="Cronología de la actividad de las aplicaciones" /></a>&nbsp;
  <a href="../images/by-type.en.png"><img src="../images/by-type.en.png" width="196" alt="Actividad agrupada por tipo" /></a>&nbsp;
  <a href="../images/apps.en.png"><img src="../images/apps.en.png" width="196" alt="Aplicaciones agrupadas por origen" /></a>&nbsp;
  <a href="../images/connection.en.png"><img src="../images/connection.en.png" width="196" alt="Cuatro modos de inicio: depuración inalámbrica, depuración por red, Shizuku y root" /></a>
</p>

<p align="center"><sub>Estas pantallas usan datos de ejemplo. Las aplicaciones y las direcciones son ficticias. Toca una imagen para ampliarla.</sub></p>

## Funciones

### Comprender la actividad

| Función | Qué puedes hacer |
| --- | --- |
| Resumen de actividad | Consulta el total, la tendencia, la media, el máximo y los accesos en segundo plano de **la sesión, hoy, 7 días o 30 días**. Toca la curva o desliza el dedo sobre ella para ver un intervalo. Los intervalos sin monitorización ni registros se marcan como **No records**. |
| Requiere atención | Usa el **mapa de calor** para ver qué días y horas destacan y revisa los envíos de datos y la actividad sensible en segundo plano, las lecturas repetidas y los intentos repetidos tras una denegación. |
| Clasificación por aplicación | Encuentra las aplicaciones más activas por accesos totales o en segundo plano y compara los datos a los que accedieron. El trabajo rutinario del sistema aparece plegado aparte. |
| Detalles de la aplicación | Sigue una sola aplicación con la misma curva. Toca un tipo de dato para mostrar solo ese tipo en la curva y la cronología. |
| Registros de actividad | Consulta **56 tipos de acceso en 12 grupos**, entre ellos la ubicación, el portapapeles y los identificadores del dispositivo. Puedes ver los registros por hora, aplicación o tipo, y filtrar la actividad en segundo plano. |
| Actividad de red | Consulta las direcciones de conexión y las mediciones de tráfico disponibles para cada aplicación. Si activas el registro de dominios, las conexiones que se puedan asociar a un dominio lo mostrarán. |
| Historial del sistema | Abre los detalles de una aplicación para consultar los registros y totales de acceso a permisos que conserva Android, incluida parte de la actividad anterior al inicio de la monitorización. |

### Monitorización y registros

| Función | Qué puedes hacer |
| --- | --- |
| Lista de exclusión de la monitorización | Elige qué aplicaciones no monitorizar en **Settings › Monitoring › Monitoring allowlist**. Jiumi no crea registros nuevos de ellas y oculta los guardados. Al quitar una, vuelven a mostrarse. La actividad del período excluido no se recupera. |
| Notificaciones | Durante la monitorización, una notificación muestra el tiempo transcurrido y un botón de detención. Puedes desactivarla en **Settings › Notifications**. Con **Show summary** muestra **los accesos de la sesión y cuántos fueron en segundo plano**, sin nombrar aplicaciones. Con **Attention alerts** recibirás un aviso cuando se detecte un nuevo motivo de atención, una vez por aplicación y motivo en cada sesión. |
| Exportación de registros | En **Settings › Logs › Export logs**, guarda los registros de un período como un archivo ZIP que contiene archivos CSV. |

## Gestión de aplicaciones

Las aplicaciones se agrupan por origen de instalación. Abre los detalles de una aplicación para revisar su actividad y decidir si quieres hacer algún cambio. Tú inicias las acciones de gestión. Después de cada cambio, Jiumi consulta el estado en Android para confirmar que se aplicó. Si Android lo rechaza, Jiumi muestra el motivo.

<p align="center">
  <a href="../images/app-disable.en.png"><img src="../images/app-disable.en.png" width="250" alt="Demostración web de la confirmación para deshabilitar una aplicación" /></a>&nbsp;
  <a href="../images/app-delete.en.png"><img src="../images/app-delete.en.png" width="250" alt="Demostración web de la confirmación de desinstalación y el aviso de copia de seguridad" /></a>&nbsp;
  <a href="../images/app-restore.en.png"><img src="../images/app-restore.en.png" width="250" alt="Demostración web de cómo restaurar una aplicación desinstalada o descartar su copia de seguridad" /></a>
</p>

| Acción | Qué ocurre |
| --- | --- |
| Permisos | Activa o desactiva los permisos uno por uno en **Permissions**, en los detalles de la aplicación. **Manageable permissions** desactiva de una vez todos los permisos que se pueden gestionar. Al volver a activar el interruptor, solo se restauran los que desactivó Jiumi. Los permisos que bloquea el sistema aparecen como de solo lectura. |
| Acceso a la red | En los dispositivos compatibles, puedes bloquear o restaurar el acceso a la red de una aplicación con el firewall de Android sin ocupar la ranura de VPN. El interruptor se bloquea y muestra el motivo si la aplicación comparte un UID con otras, si otra herramienta estableció la regla o si Jiumi no puede confirmar que solo se verá afectada esa aplicación. |
| Deshabilitar o habilitar | Android deshabilita la aplicación para el usuario actual y conserva la aplicación y sus datos. Puedes volver a habilitarla en **Apps › Disabled**. Las aplicaciones deshabilitadas llevan un distintivo con un copo de nieve. |
| Habilitada de nuevo | Si Jiumi detecta que una aplicación que deshabilitó vuelve a estar habilitada, la marca como **Re-enabled** en la lista y en sus detalles, y registra la hora de detección en la cronología. Si activas **Settings › App management › Disable again when re-enabled**, Jiumi volverá a deshabilitarla. |
| Desinstalar una aplicación que instalaste | Escribe el nombre completo del paquete para confirmar. Antes de desinstalar, Jiumi guarda una copia del APK base y de los APK divididos que tenga. Puedes intentar reinstalar la aplicación desde **Apps › Uninstalled**. **La desinstalación borra los datos de la aplicación. La copia de seguridad solo contiene los archivos de instalación.** |
| Desinstalar una aplicación preinstalada | Activa **Allow uninstalling preinstalled apps** en **Settings › App management** para desinstalar aplicaciones preinstaladas compatibles para el usuario actual. La copia de fábrica y los datos se conservan. Puedes intentar restaurar la aplicación desde **Apps › Uninstalled**. Las aplicaciones desinstaladas llevan un distintivo con una papelera. Si está desactivado, al tocar **Uninstall** Jiumi lo explica y te lleva a **App management**. |

El campo **Runs as**, en **Settings › App management**, indica con qué identidad se ejecutan estas acciones. Con root o con Shizuku iniciado como root, la deshabilitación y la habilitación de aplicaciones, y la desinstalación y la restauración de aplicaciones preinstaladas se ejecutan como root. Así Jiumi puede cambiar aplicaciones del sistema que el teléfono no permite modificar al usuario shell. Con la depuración inalámbrica o por red, las acciones se ejecutan como usuario shell. Si el teléfono impide cambiar alguna aplicación del sistema, Jiumi explica el motivo. Desinstalar una aplicación que tú instalaste solo afecta al usuario actual, por lo que se conserva cualquier copia en un perfil de trabajo o en un segundo espacio.

Los componentes esenciales del sistema están protegidos de forma predeterminada. Cambiar un componente del que depende el teléfono puede afectar a funciones del sistema. Jiumi muestra un aviso antes de hacerlo.

## ¿Cómo detecta Jiumi la actividad en segundo plano sin root?

Mientras se ejecutan las aplicaciones, Android genera registros de acceso a permisos, tablas de conexiones, trazas del sistema y registros de eventos. El monitor de Jiumi lee las señales disponibles como **usuario shell**. La aplicación las organiza mediante reglas y las guarda en una base de datos en el teléfono. La monitorización no inyecta código en otras aplicaciones. Las acciones de deshabilitar o desinstalar aplicaciones, revocar permisos o cortar el acceso a la red requieren que las inicies desde la gestión de aplicaciones.

<p align="center"><img src="../images/how-it-works.en.png" alt="Las aplicaciones generan señales del sistema Android. El monitor de Jiumi las lee como usuario shell y la aplicación organiza y guarda los registros en el teléfono. El registro opcional de dominios recibe las consultas DNS a través de una VPN local." width="900" /></p>

| Fuente de señales | Qué puede ver Jiumi |
| --- | --- |
| Registros de acceso a permisos (AppOps) | Qué aplicación usó un permiso y cuándo, junto con el historial que conserva Android. |
| Tablas de conexiones | A qué direcciones se conectaron las aplicaciones. De forma predeterminada se toma una muestra cada cinco segundos. |
| Trazas del sistema (Perfetto) | Llamadas identificables a servicios del sistema y tráfico por conexión cuando el sistema lo admite. |
| Registros de eventos | Cuándo se inició un proceso y qué motivo registró Android. |

### Cuatro formas de conexión

| Método | Ideal para | Primera configuración | Tras reiniciar el teléfono |
| --- | --- | --- | --- |
| **Depuración inalámbrica** (recomendada) | La mayoría de los teléfonos, sin root ni ordenador | Conéctate a Wi-Fi e introduce un código una vez en el teléfono | El emparejamiento se conserva. Wi-Fi y la depuración inalámbrica deben estar activos |
| **Depuración por red** | Teléfonos con un puerto ADB ya abierto | Busca o introduce el puerto y autoriza en Android si se solicita. Sin código | El teléfono debe volver a abrir el puerto |
| **Shizuku** | Quienes ya usan Shizuku | Inicia Shizuku y autoriza a Jiumi | Shizuku debe estar en ejecución |
| **Root** | Teléfonos con root | Autoriza a Jiumi en el gestor de root | Funciona mientras se mantenga el permiso |

Los cuatro ejecutan el monitor como usuario shell. Solo con root, o con Shizuku iniciado como root, las acciones de gestión indicadas arriba se ejecutan como root.

> **Seguridad de la depuración por red:** Jiumi solo se conecta a la dirección local del teléfono y nunca abre un puerto por ti. Un puerto abierto con `adb tcpip` u otro método similar suele ser accesible para otros dispositivos de la misma red. Úsalo solo en redes de confianza, no aceptes solicitudes de depuración desconocidas y cierra el puerto cuando no lo necesites.

El **registro opcional de dominios** usa una VPN local para recibir consultas DNS y reenviarlas sin cambios a los servidores DNS de la red actual. El resto del tráfico va directamente a su destino. El registro de dominios ocupa la ranura de VPN del sistema. Las consultas que usan el DNS cifrado de una aplicación o un proveedor de DNS privado especificado por nombre de host pueden quedar fuera del registro.

## Descargar e iniciar la monitorización

1. En un teléfono ARM64 con Android 11 o posterior, descarga e instala el APK desde [GitHub Releases](https://github.com/ybblab/Jiumi/releases/latest).
2. La primera vez que abras Jiumi, elige cómo iniciarlo. Si usas la depuración inalámbrica, que es el método recomendado, conéctate a Wi-Fi y sigue las indicaciones para permitir las notificaciones y activar las opciones de desarrollador. Introduce el código de emparejamiento de seis dígitos que muestra el sistema en la notificación de Jiumi. Si no puedes usar la notificación, toca “Can’t type in the notification?” en el paso 3 e introduce el código en pantalla dividida, en una ventana pequeña o desde el mosaico de Ajustes rápidos. Si ya hay un puerto ADB abierto, también puedes elegir la depuración por red.
3. Pulsa **Start monitoring** en la pantalla **Monitor**. Mientras la monitorización esté activa, aparecerá una notificación persistente. Puedes detenerla desde la notificación o desde la aplicación.

<p align="center">
  <a href="../images/pairing-split.en.png"><img src="../images/pairing-split.en.png" width="196" alt="Otras formas de emparejar: pantalla dividida o ventana pequeña" /></a>&nbsp;
  <a href="../images/pairing-tile.en.png"><img src="../images/pairing-tile.en.png" width="196" alt="Otras formas de emparejar: mosaico de Ajustes rápidos" /></a>
</p>

Después de reiniciar el teléfono, actualizar Jiumi o sufrir una interrupción del servicio de depuración, Jiumi intenta reanudar la misma sesión cuando la conexión seleccionada está disponible, y de nuevo cuando vuelves a la aplicación. Si la tarjeta muestra **Monitoring interrupted**, restablece la conexión y pulsa **Resume**. El tiempo de interrupción no cuenta como tiempo de monitorización.

Si el monitor se detiene al usar la depuración por red, Jiumi prueba el último puerto usado o vuelve a buscar un puerto local para reanudar la sesión. La tarjeta avisa si no encuentra un puerto o necesita autorización.

Si usas la depuración inalámbrica, activar también la depuración USB ayuda a mantener el monitor en funcionamiento si Android la desactiva al perderse la conexión Wi-Fi. **Settings › Connection › Backup connection** está desactivado de forma predeterminada y solo sirve si el sistema ya ofrece un puerto ADB restringido al propio teléfono. Jiumi lo verifica y reutiliza, sin abrir otro. Consulta la [guía de configuración](https://jiumi.ybblab.com/en/download/) para más detalles.

## Privacidad y limitaciones

- **Los registros se guardan en el teléfono.** Jiumi no tiene cuentas ni sube los registros de monitorización. Puede buscar actualizaciones en GitHub. Si decides actualizar, Jiumi descarga el APK y verifica su tamaño, su hash SHA-256 y su firma. Puedes desactivar la búsqueda de actualizaciones.
- **Los registros describen la actividad y las conexiones.** Jiumi puede mostrar que se usó un permiso o que se denegó un acceso. No puede ver mensajes, contraseñas ni el contenido del tráfico de red.
- **La cobertura depende del sistema.** Las conexiones breves pueden terminar entre dos muestras y algunas acciones, como la lectura directa de archivos, pueden no producir señales utilizables. Las estadísticas de tráfico por conexión requieren Android 14 o posterior y que el sistema las admita. Para reducir los falsos avisos, Jiumi omite parte de la actividad que no puede confirmar.
- **El resumen y la clasificación cuentan de forma distinta.** El resumen añade los recuentos por intervalos que conserva Android, que pueden incluir un tiempo anterior a la monitorización. La clasificación solo cuenta los registros individuales de Jiumi. En sesiones de más de 31 días, el resumen **Session** y el de la notificación solo cuentan los primeros 31; usa **30 days**.
- **La monitorización consume más batería.** El consumo depende de la actividad de las aplicaciones. Cuando dejes de necesitarla, detén la monitorización desde la notificación.

## Compilar desde el código fuente

Se necesitan JDK 17 y el SDK de Android.

```sh
cd code
./gradlew :app:assembleDebug
```

Las categorías de monitorización, las reglas de los avisos y las aplicaciones protegidas se configuran en el [directorio de reglas](../../code/app/src/main/assets/rules/). Consulta [CONTRIBUTING.md](../../CONTRIBUTING.md) para conocer la estructura del código y cómo colaborar.

## Contribuciones y licencia

Los informes de problemas y las solicitudes de incorporación de cambios son bienvenidos. Informa de los problemas de seguridad por vía privada siguiendo las instrucciones de [SECURITY.md](../../SECURITY.md).

Jiumi se distribuye bajo la [licencia Apache 2.0](../../LICENSE). Los componentes de terceros y sus licencias figuran en [NOTICE](../../NOTICE). Usa Jiumi solo en dispositivos que sean tuyos o que estés autorizado a usar.

## Historial de estrellas

<a href="https://www.star-history.com/#ybblab/Jiumi&amp;Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=ybblab/Jiumi&amp;type=Date&amp;theme=dark" />
    <img alt="Gráfico del historial de estrellas de Jiumi en GitHub" src="https://api.star-history.com/svg?repos=ybblab/Jiumi&amp;type=Date" />
  </picture>
</a>

<div align="center">

**Si Jiumi te ayuda a entender qué hacen tus aplicaciones, dale una ⭐ en GitHub.**

</div>
