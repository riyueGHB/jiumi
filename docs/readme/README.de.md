<div align="center">

<img src="../images/jiumi-icon.png" alt="Jiumi-App-Symbol" width="80" />

# Jiumi 揪咪

Sieh, was Apps im Vorder- und Hintergrund tun, und entscheide, ob du sie behältst, einschränkst, deaktivierst oder deinstallierst.
<br>Root-Zugriff und Computer sind nicht erforderlich. Die Aufzeichnungen bleiben auf deinem Smartphone.

**[APK herunterladen](https://github.com/ybblab/Jiumi/releases/latest)** · [Anleitung](https://jiumi.ybblab.com/en/download/) · [Website](https://jiumi.ybblab.com/en/)

<sub>Android 11 oder neuer · ARM64 · APK ca. 4 MB · <a href="../../LICENSE">Apache 2.0</a></sub>

[简体中文](../../README.md) · [English](README.en.md) · [日本語](README.ja.md) · [한국어](README.ko.md) · [Español](README.es.md) · **Deutsch** · [Français](README.fr.md) · [हिन्दी](README.hi.md) · [Bahasa Indonesia](README.id.md) · [Português (Brasil)](README.pt-BR.md) · [Русский](README.ru.md)

</div>

## Die App im Überblick

<p align="center">
  <a href="../images/monitor.en.png"><img src="../images/monitor.en.png" width="196" alt="30 Tage Aktivität mit Heatmap" /></a>&nbsp;
  <a href="../images/monitor-week.en.png"><img src="../images/monitor-week.en.png" width="196" alt="7-Tage-Verlauf mit Heatmap" /></a>&nbsp;
  <a href="../images/by-app.en.png"><img src="../images/by-app.en.png" width="196" alt="Apps nach Zugriffen und Hintergrundaktivität sortiert" /></a>&nbsp;
  <a href="../images/app-detail.en.png"><img src="../images/app-detail.en.png" width="196" alt="Aktivitätskurve einer App über 30 Tage" /></a>
</p>

<p align="center">
  <a href="../images/timeline.en.png"><img src="../images/timeline.en.png" width="196" alt="Zeitachse der App-Aktivität" /></a>&nbsp;
  <a href="../images/by-type.en.png"><img src="../images/by-type.en.png" width="196" alt="Aktivität nach Typ gruppiert" /></a>&nbsp;
  <a href="../images/apps.en.png"><img src="../images/apps.en.png" width="196" alt="Apps nach Herkunft gruppiert" /></a>&nbsp;
  <a href="../images/connection.en.png"><img src="../images/connection.en.png" width="196" alt="Vier Startmethoden: drahtloses Debugging, Netzwerk-Debugging, Shizuku und Root" /></a>
</p>

<p align="center"><sub>Diese Ansichten zeigen Beispieldaten. Apps und Adressen sind erfunden. Tippe auf ein Bild, um es zu vergrößern.</sub></p>

## Funktionen

### Aktivität verstehen

| Funktion | Was du damit tun kannst |
| --- | --- |
| Aktivitätsübersicht | Sieh Gesamtzahl, Verlauf, Durchschnitt, Spitze und Hintergrundzugriffe für **Sitzung, heute, 7 Tage oder 30 Tage**. Tippe auf die Kurve oder wische darüber, um einen Zeitabschnitt abzulesen. Abschnitte ohne Überwachung und Einträge sind mit **No records** markiert. |
| Auffällige Aktivitäten | Erkenne mit der **Heatmap** auffällige Tage und Stunden und prüfe Datenübertragungen und sensible Aktivitäten im Hintergrund, wiederholte Lesezugriffe und wiederholte Versuche nach einer Ablehnung. |
| Rangliste nach App | Finde die aktivsten Apps nach allen Zugriffen oder nach Hintergrundzugriffen und vergleiche die abgerufenen Daten. Routinearbeit des Systems ist separat eingeklappt. |
| App-Details | Verfolge eine einzelne App mit derselben Kurve. Tippe auf eine Datenart, um in Kurve und Zeitachse nur diese anzuzeigen. |
| Aktivitätsprotokoll | Sieh dir **56 Zugriffsarten in 12 Gruppen** an, darunter Standort, Zwischenablage und Gerätekennungen. Zeige die Einträge nach Zeit, App oder Typ an und filtere sie nach Hintergrundaktivitäten. |
| Netzwerkaktivität | Sieh dir pro App die Verbindungsadressen und verfügbaren Messwerte zum Datenverkehr an. Bei aktiver Domainerfassung können Verbindungen mit eindeutiger Zuordnung eine Domain anzeigen. |
| Systemverlauf | Öffne die App-Details, um die von Android gespeicherten Einträge und Gesamtzahlen zu Berechtigungszugriffen einzusehen. Dazu gehören auch einige Aktivitäten vor Beginn der Überwachung. |

### Überwachung und Aufzeichnungen

| Funktion | Was du damit tun kannst |
| --- | --- |
| Ausnahmeliste der Überwachung | Wähle unter **Settings › Monitoring › Monitoring allowlist** Apps, die nicht überwacht werden. Jiumi legt für sie keine neuen Einträge an und blendet vorhandene aus. Nach dem Entfernen erscheinen sie wieder. Aktivität aus der Ausnahmezeit wird nicht nachgetragen. |
| Benachrichtigungen | Während der Überwachung zeigt eine Benachrichtigung die Laufzeit und eine Schaltfläche zum Beenden an. Unter **Settings › Notifications** kannst du sie abschalten. Mit **Show summary** zeigt sie **die Zugriffe der Sitzung und deren Anteil im Hintergrund**, ohne App-Namen. Mit **Attention alerts** erhältst du bei einer neu erkannten Auffälligkeit eine Benachrichtigung, einmal je App und Auffälligkeit pro Sitzung. |
| Protokollexport | Speichere unter **Settings › Logs › Export logs** die Einträge für einen ausgewählten Zeitraum als ZIP-Archiv mit CSV-Dateien. |

## Apps verwalten

Die Apps sind nach Installationsquelle gruppiert. Öffne die Details einer App, um ihre Aktivitäten zu prüfen und zu entscheiden, ob du etwas ändern möchtest. Du stößt die Verwaltungsaktionen an. Nach jeder Änderung liest Jiumi den Zustand von Android erneut aus, um zu prüfen, ob die Änderung wirksam wurde. Wenn Android sie ablehnt, zeigt Jiumi den Grund an.

<p align="center">
  <a href="../images/app-disable.en.png"><img src="../images/app-disable.en.png" width="250" alt="Website-Demo mit Bestätigung zum Deaktivieren einer App" /></a>&nbsp;
  <a href="../images/app-delete.en.png"><img src="../images/app-delete.en.png" width="250" alt="Website-Demo mit Bestätigung der Deinstallation und Hinweis zur Sicherung" /></a>&nbsp;
  <a href="../images/app-restore.en.png"><img src="../images/app-restore.en.png" width="250" alt="Website-Demo zum Wiederherstellen einer deinstallierten App oder Verwerfen ihrer Sicherung" /></a>
</p>

| Aktion | Was dabei passiert |
| --- | --- |
| Berechtigungen | Unter **Permissions** in den App-Details kannst du Berechtigungen einzeln aus- und einschalten. Mit **Manageable permissions** schaltest du alle verwaltbaren Berechtigungen auf einmal aus. Beim Wiedereinschalten stellt Jiumi nur die Berechtigungen wieder her, die es selbst ausgeschaltet hat. Vom System gesperrte Berechtigungen werden als schreibgeschützt angezeigt. |
| Netzwerkzugriff | Auf unterstützten Geräten kannst du den Netzwerkzugriff einer einzelnen App mit der Android-eigenen Firewall sperren oder wiederherstellen. Der VPN-Platz bleibt dabei frei. Der Schalter wird mit Angabe des Grundes gesperrt, wenn die App eine UID mit anderen Apps teilt, ein anderes Tool die Regel gesetzt hat oder Jiumi nicht bestätigen kann, dass nur diese App betroffen ist. |
| App deaktivieren oder aktivieren | Android deaktiviert die App für den aktuellen Benutzer. Die App und ihre Daten bleiben erhalten. Unter **Apps › Disabled** kannst du die App wieder aktivieren. Deaktivierte Apps tragen ein Schneeflockensymbol. |
| Erneut aktiviert | Stellt Jiumi fest, dass eine von ihm deaktivierte App wieder aktiv ist, erscheint **Re-enabled** in der Liste und in den App-Details. Der Zeitverlauf hält den Zeitpunkt der Erkennung fest. Wenn du **Settings › App management › Disable again when re-enabled** einschaltest, deaktiviert Jiumi die App erneut. |
| Selbst installierte App deinstallieren | Gib zur Bestätigung den vollständigen Paketnamen ein. Vor der Deinstallation sichert Jiumi die Basis-APK und alle vorhandenen Split-APKs. Unter **Apps › Uninstalled** kannst du versuchen, die App erneut zu installieren. **Bei der Deinstallation werden die App-Daten gelöscht. Die Sicherung enthält nur die Installationsdateien.** |
| Vorinstallierte App deinstallieren | Aktiviere **Allow uninstalling preinstalled apps** unter **Settings › App management**, um unterstützte vorinstallierte Apps für den aktuellen Benutzer zu deinstallieren. Die Werkskopie und die App-Daten bleiben erhalten. Unter **Apps › Uninstalled** kannst du versuchen, die App wiederherzustellen. Deinstallierte Apps tragen ein Papierkorbsymbol. Ist die Option aus, erklärt Jiumi dies beim Tippen auf **Uninstall** und führt dich zu **App management**. |

Unter **Settings › App management** zeigt **Runs as**, mit welcher Benutzerkennung die Aktionen ausgeführt werden. Mit Root-Zugriff oder mit als Root gestartetem Shizuku werden das Deaktivieren und Aktivieren von Apps sowie das Deinstallieren und Wiederherstellen vorinstallierter Apps als Root ausgeführt. So kann Jiumi auch System-Apps ändern, die der Shell-Benutzer auf dem Gerät nicht ändern darf. Beim drahtlosen Debugging und beim Netzwerk-Debugging erfolgen diese Aktionen als Shell-Benutzer. Wenn das Gerät Änderungen an einer System-App blockiert, zeigt Jiumi den Grund an. Das Deinstallieren einer selbst installierten App betrifft nur den aktuellen Benutzer. Eine Kopie im Arbeitsprofil oder in einem zweiten Bereich bleibt erhalten.

Wichtige Systemkomponenten sind standardmäßig geschützt. Änderungen an einer Komponente, auf die das Smartphone angewiesen ist, können Systemfunktionen beeinträchtigen. Jiumi warnt vor solchen Änderungen.

## Wie erkennt Jiumi Hintergrundaktivitäten ohne Root-Zugriff?

Während Apps laufen, stellt Android Protokolle zu Berechtigungszugriffen, Verbindungstabellen, System-Traces und Ereignisprotokolle bereit. Der Jiumi-Monitor liest die verfügbaren Systemsignale als **Shell-Benutzer**. Die App ordnet sie anhand von Regeln und speichert sie in einer Datenbank auf deinem Smartphone. Die Überwachung schleust keinen Code in andere Apps ein. Aktionen wie das Deaktivieren oder Deinstallieren von Apps, Entziehen von Berechtigungen oder Sperren des Netzwerkzugriffs musst du in der App-Verwaltung selbst auslösen.

<p align="center"><img src="../images/how-it-works.en.png" alt="Apps erzeugen Android-Systemsignale. Jiumi liest sie als Shell-Benutzer, ordnet die Einträge und speichert sie auf dem Gerät. Die optionale Domainerfassung empfängt DNS-Anfragen über ein lokales VPN." width="900" /></p>

| Signalquelle | Was Jiumi erkennen kann |
| --- | --- |
| Protokolle zu Berechtigungszugriffen (AppOps) | Welche App wann eine Berechtigung genutzt hat, einschließlich des von Android gespeicherten Verlaufs. |
| Verbindungstabellen | Mit welchen Adressen sich Apps verbunden haben. Standardmäßig werden die Tabellen alle fünf Sekunden abgetastet. |
| System-Traces (Perfetto) | Erkennbare Aufrufe von Systemdiensten und Datenverkehr pro Verbindung, sofern das System dies unterstützt. |
| Ereignisprotokolle | Wann ein Prozess gestartet wurde und welchen Grund Android dafür erfasst hat. |

### Vier Verbindungsarten

| Methode | Geeignet für | Ersteinrichtung | Nach einem Neustart |
| --- | --- | --- | --- |
| **Drahtloses Debugging** (empfohlen) | Die meisten Smartphones, ohne Root und Computer | Mit WLAN verbinden und einmal einen Kopplungscode eingeben | Die Kopplung bleibt. WLAN und drahtloses Debugging müssen an sein |
| **Netzwerk-Debugging** | Smartphones mit bereits offenem ADB-Port | Port suchen oder eingeben und bei Bedarf in Android bestätigen. Ohne Kopplungscode | Das Smartphone muss den Port erneut öffnen |
| **Shizuku** | Wer Shizuku bereits nutzt | Shizuku starten und Jiumi erlauben | Shizuku muss laufen |
| **Root** | Gerootete Smartphones | Jiumi im Root-Manager erlauben | Funktioniert, solange die Freigabe besteht |

Alle vier führen den Monitor als Shell-Benutzer aus. Nur mit Root oder mit als Root gestartetem Shizuku laufen die oben genannten Verwaltungsaktionen als Root.

> **Sicherheit beim Netzwerk-Debugging:** Jiumi verbindet sich nur mit der lokalen Adresse des Smartphones und öffnet nie einen Port für dich. Ein mit `adb tcpip` oder ähnlich geöffneter Port ist meist auch für andere Geräte im selben Netzwerk erreichbar. Nutze ihn nur in vertrauenswürdigen Netzwerken, bestätige keine unbekannten Debugging-Anfragen und schließe den Port, wenn du ihn nicht brauchst.

Die **optionale Domainerfassung** empfängt DNS-Anfragen über ein lokales VPN und leitet sie unverändert an die DNS-Server des aktuellen Netzwerks weiter. Der übrige Datenverkehr geht direkt ans Ziel. Die Domainerfassung belegt den VPN-Platz des Systems. Wenn eine App eigenes verschlüsseltes DNS nutzt oder ein privater DNS-Anbieter per Hostname konfiguriert ist, werden die Anfragen möglicherweise nicht erfasst.

## Herunterladen und Überwachung starten

1. Lade auf einem ARM64-Smartphone mit Android 11 oder neuer die APK von [GitHub Releases](https://github.com/ybblab/Jiumi/releases/latest) herunter und installiere sie.
2. Wähle beim ersten Öffnen von Jiumi eine Startmethode. Für die empfohlene Methode, drahtloses Debugging, verbinde dich mit dem WLAN und folge der Einrichtung, um Benachrichtigungen zu erlauben und die Entwickleroptionen zu aktivieren. Gib in der Benachrichtigung von Jiumi den sechsstelligen Kopplungscode ein, den Android anzeigt. Lässt sich die Benachrichtigung nicht nutzen, tippe in Schritt 3 auf “Can’t type in the notification?” und gib den Code stattdessen im geteilten Bildschirm, in einem kleinen Fenster oder über die Schnelleinstellungs-Kachel ein. Ist bereits ein ADB-Port offen, kannst du auch Netzwerk-Debugging wählen.
3. Tippe auf der Seite **Monitor** auf **Start monitoring**. Während der Überwachung erscheint eine dauerhafte Benachrichtigung. Du kannst die Überwachung über die Benachrichtigung oder in der App beenden.

<p align="center">
  <a href="../images/pairing-split.en.png"><img src="../images/pairing-split.en.png" width="196" alt="Weitere Kopplungswege: geteilter Bildschirm oder kleines Fenster" /></a>&nbsp;
  <a href="../images/pairing-tile.en.png"><img src="../images/pairing-tile.en.png" width="196" alt="Weitere Kopplungswege: Schnelleinstellungs-Kachel" /></a>
</p>

Nach einem Neustart, einem Jiumi-Update oder einer Unterbrechung des Debugging-Dienstes versucht Jiumi, dieselbe Sitzung fortzusetzen, sobald die gewählte Verbindung verfügbar ist, und erneut, wenn du zur App zurückkehrst. Wenn die Karte **Monitoring interrupted** anzeigt, stelle die Verbindung wieder her und tippe auf **Resume**. Die Zeit der Unterbrechung zählt nicht zur Überwachungsdauer.

Stoppt der Monitor beim Netzwerk-Debugging, probiert Jiumi den zuletzt genutzten Port oder sucht erneut einen lokalen Port, um die Sitzung fortzusetzen. Die Karte meldet, wenn kein Port gefunden wird oder eine Freigabe nötig ist.

Wenn du drahtloses Debugging nutzt, kann zusätzlich aktiviertes USB-Debugging den laufenden Monitor aufrechterhalten, falls Android es beim Verlust der WLAN-Verbindung abschaltet. **Settings › Connection › Backup connection** ist standardmäßig aus und nur für Smartphones gedacht, deren System bereits einen auf das Gerät beschränkten ADB-Port bereitstellt. Jiumi prüft und nutzt diesen Port und öffnet keinen neuen. Details findest du in der [Einrichtungsanleitung](https://jiumi.ybblab.com/en/download/).

## Datenschutz und Grenzen

- **Die Aufzeichnungen bleiben auf deinem Smartphone.** Jiumi verwendet keine Benutzerkonten und lädt keine Überwachungsdaten hoch. Die App kann auf GitHub nach Updates suchen. Wenn du ein Update auswählst, lädt sie die APK herunter und prüft Dateigröße, SHA-256-Prüfsumme und Signatur. Die Updatesuche lässt sich abschalten.
- **Die Einträge beschreiben Aktivitäten und Verbindungen.** Jiumi kann anzeigen, dass eine Berechtigung verwendet oder ein Zugriff abgelehnt wurde. Nachrichten, Passwörter und Inhalte des Netzwerkverkehrs kann die App nicht einsehen.
- **Was erfasst werden kann, hängt vom System ab.** Kurze Verbindungen können zwischen zwei Abfragen enden. Aktionen wie direkte Dateizugriffe liefern möglicherweise kein auswertbares Signal. Messwerte zum Datenverkehr pro Verbindung setzen Android 14 oder neuer und passende Systemunterstützung voraus. Jiumi lässt einige nicht bestätigbare Aktivitäten weg, um Fehlmeldungen zu reduzieren.
- **Übersicht und Rangliste zählen unterschiedlich.** Die Übersicht ergänzt die von Android nach Zeitabschnitten gespeicherten Zahlen, die Zeit vor Beginn der Überwachung enthalten können. Die Rangliste zählt nur Jiumis Einzeleinträge. Dauert eine Sitzung länger als 31 Tage, zählen die Übersicht **Session** und die Benachrichtigung nur die ersten 31 Tage. Nutze dann **30 days**.
- **Die Überwachung verbraucht zusätzlich Akku.** Wie viel, hängt von der App-Aktivität ab. Beende die Überwachung über die Benachrichtigung, wenn du sie nicht mehr brauchst.

## Aus dem Quellcode bauen

Du brauchst JDK 17 und das Android SDK.

```sh
cd code
./gradlew :app:assembleDebug
```

Überwachungskategorien, Regeln für Auffälligkeiten und geschützte Apps sind im [Regelverzeichnis](../../code/app/src/main/assets/rules/) konfiguriert. Den Aufbau des Codes und Hinweise zur Mitarbeit findest du in [CONTRIBUTING.md](../../CONTRIBUTING.md).

## Mitarbeit und Lizenz

Issues und Pull Requests sind willkommen. Bitte melde Sicherheitsprobleme vertraulich nach den Hinweisen in [SECURITY.md](../../SECURITY.md).

Jiumi steht unter der [Apache License 2.0](../../LICENSE). Drittanbieterkomponenten und ihre Lizenzen stehen in [NOTICE](../../NOTICE). Verwende Jiumi auf Geräten, die dir gehören oder die du mit Erlaubnis nutzen darfst.

## Verlauf der GitHub-Sterne

<a href="https://www.star-history.com/#ybblab/Jiumi&amp;Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=ybblab/Jiumi&amp;type=Date&amp;theme=dark" />
    <img alt="Verlauf der GitHub-Sterne von Jiumi" src="https://api.star-history.com/svg?repos=ybblab/Jiumi&amp;type=Date" />
  </picture>
</a>

<div align="center">

**Wenn Jiumi dir hilft, die Aktivitäten deiner Apps zu verstehen, gib dem Projekt auf GitHub einen ⭐.**

</div>
