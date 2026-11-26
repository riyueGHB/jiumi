<div align="center">

<img src="../images/jiumi-icon.png" alt="Icône de Jiumi" width="80" />

# Jiumi 揪咪

Voyez ce que font les applications au premier plan et en arrière-plan, puis choisissez de les garder, de les restreindre, de les désactiver ou de les désinstaller.
<br>Aucun accès root ni ordinateur nécessaire. Les données enregistrées restent sur le téléphone.

**[Télécharger l’APK](https://github.com/ybblab/Jiumi/releases/latest)** · [Guide](https://jiumi.ybblab.com/en/download/) · [Site web](https://jiumi.ybblab.com/en/)

<sub>Android 11 ou version ultérieure · ARM64 · APK d’environ 4 Mo · <a href="../../LICENSE">Apache 2.0</a></sub>

[简体中文](../../README.md) · [English](README.en.md) · [日本語](README.ja.md) · [한국어](README.ko.md) · [Español](README.es.md) · [Deutsch](README.de.md) · **Français** · [हिन्दी](README.hi.md) · [Bahasa Indonesia](README.id.md) · [Português (Brasil)](README.pt-BR.md) · [Русский](README.ru.md)

</div>

## Aperçu de l’application

<p align="center">
  <a href="../images/monitor.en.png"><img src="../images/monitor.en.png" width="196" alt="30 jours d’activité avec carte de chaleur" /></a>&nbsp;
  <a href="../images/monitor-week.en.png"><img src="../images/monitor-week.en.png" width="196" alt="Tendance sur 7 jours avec carte de chaleur" /></a>&nbsp;
  <a href="../images/by-app.en.png"><img src="../images/by-app.en.png" width="196" alt="Applications classées par accès et activité en arrière-plan" /></a>&nbsp;
  <a href="../images/app-detail.en.png"><img src="../images/app-detail.en.png" width="196" alt="Courbe d’activité d’une application sur 30 jours" /></a>
</p>

<p align="center">
  <a href="../images/timeline.en.png"><img src="../images/timeline.en.png" width="196" alt="Chronologie de l’activité des applications" /></a>&nbsp;
  <a href="../images/by-type.en.png"><img src="../images/by-type.en.png" width="196" alt="Activité regroupée par type" /></a>&nbsp;
  <a href="../images/apps.en.png"><img src="../images/apps.en.png" width="196" alt="Applications regroupées par origine" /></a>&nbsp;
  <a href="../images/connection.en.png"><img src="../images/connection.en.png" width="196" alt="Quatre modes de démarrage : débogage sans fil, débogage réseau, Shizuku et root" /></a>
</p>

<p align="center"><sub>Ces écrans utilisent des données d’exemple. Les applications et les adresses sont fictives. Touchez une image pour l’agrandir.</sub></p>

## Fonctionnalités

### Comprendre l’activité

| Fonctionnalité | Ce que vous pouvez faire |
| --- | --- |
| Vue d’ensemble | Consultez le total, la tendance, la moyenne, le pic et les accès en arrière-plan pour **la session, aujourd’hui, 7 jours ou 30 jours**. Touchez ou parcourez la courbe pour lire une période. Les périodes sans surveillance ni données portent la mention **No records**. |
| Activités à examiner | Repérez avec la **carte de chaleur** les jours et les heures à examiner, et consultez les envois de données et les activités sensibles en arrière-plan, les lectures répétées et les tentatives répétées après un refus. |
| Classement par application | Trouvez les applications les plus actives, au total ou en arrière-plan, et comparez les données consultées. Les tâches système courantes sont repliées à part. |
| Fiche d’application | Suivez une seule application sur la même courbe. Touchez un type de données pour n’afficher que lui dans la courbe et la chronologie. |
| Journal d’activité | Consultez **56 types d’accès répartis en 12 groupes**, dont les accès à la localisation, au presse-papiers et aux identifiants de l’appareil. Affichez les événements par heure, application ou type, et filtrez les activités en arrière-plan. |
| Activité réseau | Consultez les adresses de connexion et les mesures de trafic disponibles pour chaque application. Lorsque la journalisation des domaines est activée, un domaine peut s’afficher pour les connexions auxquelles il a pu être associé. |
| Historique système | Ouvrez la fiche d’une application pour consulter les accès aux autorisations et les totaux conservés par Android. Ils incluent certaines activités qui précèdent le début de la surveillance. |

### Surveillance et journaux

| Fonctionnalité | Ce que vous pouvez faire |
| --- | --- |
| Liste d’exclusion de la surveillance | Choisissez les applications à ne pas surveiller dans **Settings › Monitoring › Monitoring allowlist**. Jiumi ne crée plus d’entrées pour elles et masque celles enregistrées. Elles réapparaissent quand vous retirez l’application. L’activité de la période exclue n’est pas récupérée. |
| Notifications | Pendant la surveillance, une notification affiche le temps écoulé et un bouton d’arrêt. Vous pouvez la désactiver dans **Settings › Notifications**. Avec **Show summary**, elle affiche **les accès de la session et ceux effectués en arrière-plan**, sans nom d’application. Avec **Attention alerts**, vous êtes averti d’une nouvelle activité à examiner, une fois par application et par type d’alerte au cours d’une session. |
| Export des journaux | Dans **Settings › Logs › Export logs**, enregistrez le journal d’une période choisie dans une archive ZIP contenant des fichiers CSV. |

## Gérer les applications

Les applications sont regroupées selon leur source d’installation. Ouvrez la fiche d’une application pour consulter son activité et décider si vous souhaitez intervenir. Vous déclenchez les actions de gestion. Après chaque modification, Jiumi relit l’état fourni par Android pour confirmer qu’elle a été appliquée. Si Android la refuse, Jiumi en affiche la raison.

<p align="center">
  <a href="../images/app-disable.en.png"><img src="../images/app-disable.en.png" width="250" alt="Démonstration sur le site de la confirmation de la désactivation d’une application" /></a>&nbsp;
  <a href="../images/app-delete.en.png"><img src="../images/app-delete.en.png" width="250" alt="Démonstration sur le site de la confirmation de la désinstallation et du message de sauvegarde" /></a>&nbsp;
  <a href="../images/app-restore.en.png"><img src="../images/app-restore.en.png" width="250" alt="Démonstration sur le site des options pour restaurer une application désinstallée ou effacer sa sauvegarde" /></a>
</p>

| Action | Résultat |
| --- | --- |
| Autorisations | Activez ou désactivez les autorisations une par une depuis **Permissions**, sur la fiche de l’application. **Manageable permissions** permet de désactiver toutes les autorisations modifiables à la fois. Lorsque vous réactivez cet interrupteur, seules les autorisations que Jiumi a désactivées sont rétablies. Les autorisations verrouillées par le système sont affichées en lecture seule. |
| Accès au réseau | Sur les appareils compatibles, bloquez ou rétablissez l’accès au réseau d’une application avec le pare-feu d’Android, sans occuper l’emplacement VPN. L’interrupteur est verrouillé et affiche la raison si l’application partage un UID avec d’autres applications, si un autre outil a défini la règle ou si Jiumi ne peut pas confirmer que seule cette application sera affectée. |
| Désactiver ou activer | Android désactive l’application pour l’utilisateur actuel et conserve l’application et ses données. Vous pouvez la réactiver dans **Apps › Disabled**. Les applications désactivées portent un symbole en forme de flocon. |
| Application réactivée | Si Jiumi détecte qu’une application qu’il a désactivée a été réactivée, **Re-enabled** apparaît dans la liste et sur sa fiche. La chronologie indique l’heure de détection. Si vous activez **Settings › App management › Disable again when re-enabled**, Jiumi désactive de nouveau l’application. |
| Désinstaller une application que vous avez installée | Saisissez le nom complet du paquet pour confirmer. Jiumi sauvegarde l’APK de base et les APK fractionnés éventuels. Vous pouvez tenter de réinstaller l’application depuis **Apps › Uninstalled**. **La désinstallation efface les données de l’application. La sauvegarde contient uniquement les fichiers d’installation.** |
| Désinstaller une application préinstallée | Activez **Allow uninstalling preinstalled apps** dans **Settings › App management** pour désinstaller les applications préinstallées compatibles pour l’utilisateur actuel. La copie d’origine et les données de l’application sont conservées. Vous pouvez tenter de restaurer l’application depuis **Apps › Uninstalled**. Les applications désinstallées portent un symbole en forme de corbeille. Si l’option est désactivée, toucher **Uninstall** l’explique et vous mène à **App management**. |

Dans **Settings › App management**, le champ **Runs as** indique sous quelle identité les actions sont exécutées. Avec un accès root, ou avec Shizuku lancé en root, la désactivation et l’activation des applications ainsi que la désinstallation et la restauration des applications préinstallées s’exécutent en root. Jiumi peut ainsi modifier les applications système que le téléphone interdit à l’utilisateur shell de modifier. Avec le débogage sans fil ou réseau, ces actions s’exécutent en tant qu’utilisateur shell. Si le téléphone bloque la modification d’une application système, Jiumi en affiche la raison. La désinstallation d’une application que vous avez installée ne concerne que l’utilisateur actuel. Une copie dans un profil professionnel ou un second espace est conservée.

Les composants essentiels du système sont protégés par défaut. Modifier un composant dont dépend le téléphone peut perturber certaines fonctions du système. Jiumi affiche un avertissement avant toute modification de ce type.

## Comment Jiumi détecte-t-il les activités en arrière-plan sans accès root ?

Pendant l’exécution des applications, Android fournit des journaux d’accès aux autorisations, des tables de connexions, des traces système et des journaux d’événements. Le moniteur de Jiumi lit les signaux disponibles en tant qu’**utilisateur shell**. L’application les organise à l’aide de règles et les enregistre dans une base de données sur votre téléphone. La surveillance n’injecte aucun code dans les autres applications. Vous devez déclencher vous-même la désactivation ou la désinstallation des applications, le retrait des autorisations ou le blocage de l’accès au réseau depuis la gestion des applications.

<p align="center"><img src="../images/how-it-works.en.png" alt="Les applications génèrent des signaux du système Android. Le moniteur de Jiumi les lit en tant qu’utilisateur shell. L’application organise les événements enregistrés et les conserve sur le téléphone. La journalisation facultative des domaines reçoit les requêtes DNS via un VPN local." width="900" /></p>

| Source des signaux | Ce que Jiumi peut voir |
| --- | --- |
| Journaux d’accès aux autorisations (AppOps) | Quelle application a utilisé une autorisation et à quel moment, avec l’historique conservé par Android. |
| Tables de connexions | Les adresses auxquelles les applications se sont connectées. Les tables sont relevées toutes les cinq secondes par défaut. |
| Traces système (Perfetto) | Les appels identifiables aux services système et le trafic par connexion quand le système le permet. |
| Journaux d’événements | Quand un processus a démarré et la raison enregistrée par Android. |

### Quatre modes de connexion

| Méthode | Convient à | Première configuration | Après un redémarrage |
| --- | --- | --- | --- |
| **Débogage sans fil** (recommandé) | La plupart des téléphones, sans root ni ordinateur | Se connecter au Wi-Fi et saisir une fois un code sur le téléphone | L’association est conservée. Le Wi-Fi et le débogage sans fil doivent être actifs |
| **Débogage réseau** | Téléphones dont un port ADB est déjà ouvert | Rechercher ou saisir le port, puis autoriser dans Android si demandé. Sans code | Le téléphone doit rouvrir le port |
| **Shizuku** | Les utilisateurs de Shizuku | Lancer Shizuku et autoriser Jiumi | Shizuku doit être lancé |
| **Root** | Téléphones rootés | Autoriser Jiumi dans le gestionnaire root | Fonctionne tant que l’autorisation est valable |

Les quatre exécutent le moniteur en tant qu’utilisateur shell. Seuls l’accès root, ou Shizuku lancé en root, exécutent en root les actions de gestion décrites plus haut.

> **Sécurité du débogage réseau :** Jiumi se connecte uniquement à l’adresse locale du téléphone et n’ouvre jamais de port à votre place. Un port ouvert avec `adb tcpip` ou une méthode similaire est en général accessible aux autres appareils du même réseau. Utilisez-le uniquement sur un réseau de confiance, refusez les demandes de débogage inconnues et fermez le port lorsque vous n’en avez plus besoin.

La **journalisation facultative des domaines** utilise un VPN local pour recevoir les requêtes DNS et les transmettre sans modification aux serveurs DNS du réseau utilisé. Le reste du trafic va directement à sa destination. La journalisation des domaines occupe l’emplacement VPN du système. Les requêtes envoyées via le DNS chiffré propre à une application ou via un fournisseur de DNS privé configuré par nom d’hôte peuvent échapper à la journalisation.

## Télécharger et démarrer la surveillance

1. Sur un téléphone ARM64 équipé d’Android 11 ou d’une version ultérieure, téléchargez et installez l’APK depuis [GitHub Releases](https://github.com/ybblab/Jiumi/releases/latest).
2. Au premier lancement de Jiumi, choisissez un mode de démarrage. Pour le débogage sans fil, qui est le mode recommandé, connectez-vous au Wi-Fi et suivez la procédure pour autoriser les notifications et activer les options pour les développeurs. Saisissez dans la notification de Jiumi le code d’association à six chiffres affiché par le système. Si la notification n’est pas utilisable, touchez “Can’t type in the notification?” à l’étape 3 et saisissez le code en écran partagé, dans une petite fenêtre ou via la vignette des Réglages rapides. Si un port ADB est déjà ouvert, vous pouvez aussi choisir le débogage réseau.
3. Dans l’écran **Monitor**, appuyez sur **Start monitoring**. Une notification permanente s’affiche tant que la surveillance est active. Vous pouvez arrêter la surveillance depuis la notification ou l’application.

<p align="center">
  <a href="../images/pairing-split.en.png"><img src="../images/pairing-split.en.png" width="196" alt="Autres façons d’associer : écran partagé ou petite fenêtre" /></a>&nbsp;
  <a href="../images/pairing-tile.en.png"><img src="../images/pairing-tile.en.png" width="196" alt="Autres façons d’associer : vignette des Réglages rapides" /></a>
</p>

Après un redémarrage du téléphone, une mise à jour de Jiumi ou une interruption du service de débogage, Jiumi tente de reprendre la même session lorsque la connexion choisie est disponible, puis à nouveau quand vous revenez dans l’application. Si la carte affiche **Monitoring interrupted**, rétablissez la connexion puis appuyez sur **Resume**. Le temps d’interruption n’est pas compté dans la durée de surveillance.

Si le moniteur s’arrête avec le débogage réseau, Jiumi essaie le dernier port utilisé ou recherche à nouveau un port local pour reprendre la session. La carte signale l’absence de port ou le besoin d’une autorisation.

Si vous utilisez le débogage sans fil, activer aussi le débogage USB aide à maintenir le moniteur en cours d’exécution si Android le désactive à la perte du Wi-Fi. **Settings › Connection › Backup connection** est désactivé par défaut et ne convient qu’aux téléphones dont le système fournit déjà un port ADB réservé à l’appareil. Jiumi le vérifie et le réutilise, sans en ouvrir un autre. Consultez le [guide de configuration](https://jiumi.ybblab.com/en/download/) pour plus de détails.

## Confidentialité et limites

- **Les données enregistrées restent sur votre téléphone.** Jiumi ne gère aucun compte et ne transfère pas les journaux de surveillance. L’application peut rechercher des mises à jour sur GitHub. Si vous choisissez d’effectuer une mise à jour, elle télécharge l’APK et vérifie sa taille, son empreinte SHA-256 et sa signature. La recherche de mises à jour peut être désactivée.
- **Le journal décrit les activités et les connexions.** Jiumi peut signaler qu’une autorisation a été utilisée ou qu’un accès a été refusé. Il ne peut pas lire les messages, les mots de passe ni le contenu du trafic réseau.
- **La couverture dépend du système.** Les connexions brèves peuvent se terminer entre deux relevés. Certaines actions, comme la lecture directe de fichiers, peuvent ne produire aucun signal exploitable. La mesure du trafic par connexion exige Android 14 ou une version ultérieure et doit être prise en charge par le système. Jiumi écarte certaines activités qu’il ne peut pas confirmer afin de limiter les fausses alertes.
- **La vue d’ensemble et le classement comptent différemment.** La vue d’ensemble ajoute les totaux par période conservés par Android, qui peuvent inclure un temps antérieur à la surveillance. Le classement ne compte que les entrées individuelles de Jiumi. Au-delà de 31 jours de session, la vue **Session** et la notification ne comptent que les 31 premiers jours ; utilisez **30 days**.
- **La surveillance augmente la consommation de batterie.** La consommation dépend de l’activité des applications. Arrêtez la surveillance depuis la notification lorsque vous n’en avez plus besoin.

## Compiler à partir du code source

JDK 17 et le SDK Android sont requis.

```sh
cd code
./gradlew :app:assembleDebug
```

Les catégories de surveillance, les règles d’alerte et les applications protégées sont définies dans le [répertoire des règles](../../code/app/src/main/assets/rules/). Consultez [CONTRIBUTING.md](../../CONTRIBUTING.md) pour connaître l’organisation du code et la marche à suivre pour contribuer.

## Contributions et licence

Les signalements de problèmes et les demandes de modification sont les bienvenus. Signalez les problèmes de sécurité en privé en suivant les indications de [SECURITY.md](../../SECURITY.md).

Jiumi est distribué sous [licence Apache 2.0](../../LICENSE). Les composants tiers et leurs licences sont répertoriés dans [NOTICE](../../NOTICE). Utilisez Jiumi sur les appareils qui vous appartiennent ou que vous êtes autorisé à utiliser.

## Historique des étoiles

<a href="https://www.star-history.com/#ybblab/Jiumi&amp;Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=ybblab/Jiumi&amp;type=Date&amp;theme=dark" />
    <img alt="Historique des étoiles de Jiumi sur GitHub" src="https://api.star-history.com/svg?repos=ybblab/Jiumi&amp;type=Date" />
  </picture>
</a>

<div align="center">

**Si Jiumi vous aide à comprendre ce que font vos applications, donnez-lui une ⭐ sur GitHub.**

</div>
