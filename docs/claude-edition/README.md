# My AI Drive — Claude Edition

Édition indépendante d'[AAIdrive](https://github.com/BimmerGestalt/AAIdrive) (BimmerGestalt, licence MIT,
voir `LICENSE` à la racine — crédits et licence conservés), développée sur la branche
`feature/my-bmw-drive-claude` à partir du commit `a388721539f59b5760297c38d81ee4822c26fb13`.
Cible : BMW 340i GT F34 (2016), iDrive 6 / NBT EVO.

| | |
|---|---|
| Nom affiché | My AI Drive Claude |
| Application ID | `com.myaidrive.claude` |
| APK | `MyAIDrive-Claude-debug.apk` (artefact GitHub Actions `MyAIDrive-Claude-debug`) |
| Variante Gradle | `nomapNonalyticsFullDebug` (sans carte, sans analytics) |

## 1. Installation sur le téléphone

1. GitHub → *Actions* → **Manual Debug APK Build** → *Run workflow* → branche `feature/my-bmw-drive-claude`.
2. Ouvrir le run terminé → *Artifacts* → télécharger **MyAIDrive-Claude-debug** (zip : APK + `.sha256`).
3. Vérifier l'empreinte SHA-256 affichée dans l'étape « Name the APK » du run avant d'installer.
4. Installer l'APK. Il **cohabite** avec AAIdrive officiel et la GPT Edition (`com.myaidrive.gpt`) : identifiant,
   autorité du provider et permissions propres à l'édition. Aucune désinstallation n'est nécessaire.
5. Si Play Protect avertit : voir §6 (cause connue des APK auto-compilés, rien n'est contourné).
6. Android 13+ : *Infos de l'appli* → ⋮ → **Autoriser les paramètres restreints**, puis activer
   **l'accès aux notifications** pour My AI Drive Claude (sans lui : applis musicales grisées, liste iDrive vide).
7. Ouvrir l'app → menu **Édition & diagnostic** : tous les contrôles doivent être ✓ (sauf Spotify tant
   qu'aucun Client ID n'est fourni, §4).

> Une mise à jour d'un APK signé par une clé différente impose de désinstaller la version précédente.
> Configurez la signature stable (§5) pour éviter cela.

## 2. Une seule édition active dans la voiture

Deux éditions qui répondent à la même session BMW Connected enregistreraient deux fois les mêmes applis RHMI
et se disputeraient la source audio. Procédure avant chaque essai :

1. Dans l'édition à tester : **Édition & diagnostic** → *Connexion à la voiture (cette édition)* = **activée**.
2. Dans les autres éditions : même interrupteur = **désactivé** (ou forcer l'arrêt / désactiver AAIdrive officiel).
3. L'écran de diagnostic signale les autres éditions installées.

Techniquement : les intents CarAPI annoncés à BMW Connected sont propres à l'édition
(`com.myaidrive.claude.CarConnectionListener_START`), et le broadcast BMW, la connexion Bluetooth,
l'ouverture de l'app et `MainService` vérifient tous l'interrupteur.

## 3. My AI Drive dans iDrive

* Menu iDrive **Informations véhicule** → **My AI Drive**.
* Six indicateurs : vitesse, température moteur, température d'huile, carburant (+ autonomie), batterie, mode de conduite.
* **Tourner la molette** : bascule M Classic ↔ M Modern. Le thème se règle aussi dans l'app Android.
* Données réelles CDS (les mêmes que « Detailed Vehicle Info ») ; une donnée non transmise s'affiche `--`.
* **Mode démonstration** (réglage Android) : valeurs simulées, bandeau **« DÉMO — VALEURS SIMULÉES »** permanent.
  Hors démo, l'aperçu Android ne montre jamais de valeur inventée.
* Images JPEG rendues directement (pas de VirtualDisplay), envoyées uniquement quand une valeur affichée change :
  2 img/s max en Bluetooth (qualité 70), 5 img/s en USB (qualité 85), rafraîchissement de sécurité toutes les 5 s.

## 4. Spotify (titres, pochettes, playlists, navigation iDrive)

Spotify refuse les connexions MediaBrowser des applis non autorisées : AAIdrive passe par le SDK **App Remote** +
**Web API**, qui exigent un Client ID enregistré pour **ce** package et **cette** signature.

1. https://developer.spotify.com/dashboard → *Create app* (Android).
2. Package : `com.myaidrive.claude` ; empreinte **SHA-1** : celle affichée dans *Édition & diagnostic*.
3. Redirect URI : `com.myaidrive.claude://spotify_callback` ; cocher *Android* et *Web API*.
4. Secret GitHub `MYAIDRIVE_CLAUDE_SPOTIFY_CLIENT_ID` = Client ID, puis relancer le workflow.

Le Client ID n'est **jamais** écrit dans le dépôt. L'empreinte SHA-1 doit rester stable : signature stable (§5) indispensable.

## 5. Signature stable (recommandée)

```sh
keytool -genkeypair -keystore myaidrive-claude.keystore -alias myaidrive-claude \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=My AI Drive Claude Edition"
base64 -w0 myaidrive-claude.keystore   # valeur du secret MYAIDRIVE_CLAUDE_KEYSTORE_BASE64
```

Secrets GitHub : `MYAIDRIVE_CLAUDE_KEYSTORE_BASE64`, `MYAIDRIVE_CLAUDE_KEYSTORE_PASSWORD`,
`MYAIDRIVE_CLAUDE_KEY_ALIAS`, `MYAIDRIVE_CLAUDE_KEY_PASSWORD`. Gardez le keystore hors du dépôt.
Sans ces secrets, le build fonctionne mais la signature change à chaque run (avertissement dans le diagnostic).

## 6. Rapport d'audit technique

### 6.1 Inventaire (hérité d'AAIdrive, conservé)
Notifications, lecture vocale, musique (MediaBrowser, MediaSession, Spotify), assistants vocaux, calendrier,
navigation (partage d'adresses), Detailed Vehicle Info, découverte CDS, provider CDS pour add-ons,
carte (variantes gmap/mapbox uniquement — **non incluse** dans l'APK Claude Edition, comme dans notre APK précédent).

### 6.2 Architecture observée
* `MainService` (service de premier plan) détecte la voiture et démarre les modules `CarAppService` (un thread et une
  connexion Etch par appli iDrive).
* iDrive n'affiche que des widgets déclarés par des descriptions RHMI **signées BMW** : AAIdrive réutilise les
  ressources d'applis BMW/Spotify/SmartThings extraites au build (`app/buildtools/external.gradle`).
* Broadcasts internes déjà limités au package (`setPackage`, `RECEIVER_NOT_EXPORTED`) — bon point hérité.

### 6.3 Régressions et corrections

| Symptôme | Cause vérifiée dans le code | Correction | Statut |
|---|---|---|---|
| Applis musicales grisées, liste iDrive vide | Toute lecture de session passe par `getActiveSessions(NotificationListener)` (`MusicSessions.kt`) ; sans accès aux notifications, tout est « bloqué » (`musicapp_listitem.xml`). Android 13+ grise ce réglage pour un APK installé hors store (l'APK officiel venait du Play Store). | Diagnostic dédié + procédure « paramètres restreints » + raccourcis | À valider sur le téléphone |
| Spotify joue sans titres/pochettes/playlists, commandes iDrive KO | Client ID Spotify `invalid` dans nos builds CI (`gradle.properties`) → App Remote/Web API jamais utilisés ; clé de debug différente à chaque build → impossible à enregistrer chez Spotify | Secret `MYAIDRIVE_CLAUDE_SPOTIFY_CLIENT_ID` + signature stable par secrets + diagnostic | Nécessite votre Client ID Spotify |
| Redirection OAuth Spotify | `REDIRECT_URI` codé en dur sur `me.hufman.androidautoidrive://` alors que le schéma AppAuth suit l'applicationId | Dérivé de `BuildConfig.APPLICATION_ID` | Corrigé |
| Installation impossible à côté d'AAIdrive | Autorité `io.bimmergestalt.cardata.provider` et permissions `io.bimmergestalt.permission.CDS_*` partagées | Autorité/permissions préfixées par l'applicationId | Corrigé |
| Deux éditions réveillées par la voiture | Intents CarAPI identiques | Intents propres à l'édition + interrupteur « édition active » | Corrigé |
| Dashboard non intégré à iDrive | Prototype phone-only | App RHMI « My AI Drive » (Informations véhicule), molette, données CDS | À valider dans la voiture |
| Un indicateur CDS absent bloquerait tout le dashboard | `combine()` attend chaque flux | Chaque métrique démarre à « indisponible » | Corrigé + testé |

### 6.4 Sécurité / Play Protect
**Confirmé (code et APK inspectés)** : permissions sensibles légitimes (accès notifications, `READ_SMS`,
`QUERY_ALL_PACKAGES`, `BLUETOOTH_CONNECT`), provider CDS exporté (VIN, GPS) protégé par permission, certificats `.p7b`
de BMW/Spotify/SmartThings embarqués (indispensables à iDrive), APK de debug signé par une clé jetable sans réputation.
**Hypothèse** : Play Protect pondère la combinaison « nouvel APK inconnu + accès notifications/SMS + certificats
d'éditeurs tiers » ; le classement exact de Google n'est pas observable depuis le code.
**Mesures** : `allowBackup=false` (le fichier de réglages contient le jeton OAuth Spotify), signature stable optionnelle,
autorités/permissions propres à l'édition, aucune permission ajoutée, aucun secret dans le dépôt.
**Risque accepté** : `CarConnectionListener` reste exporté (requis par BMW Connected) ; un tiers ne peut au pire
que démarrer/arrêter notre service, désormais aussi conditionné à l'interrupteur d'édition.
**Compatibilité add-ons** : les add-ons BimmerGestalt existants ciblent `io.bimmergestalt.*` et ne voient pas
l'édition Claude (prix de la cohabitation).

## 7. Tests

Tests unitaires ajoutés (exécutés par le workflow, rapport en artefact `MyAIDrive-Claude-unit-tests`) :
`DashboardFormatTest`, `DashboardDataSourceTest`, `EditionDiagnosticsTest`, plus toute la suite existante.
**Une compilation et des tests verts ne valident pas le comportement dans la voiture.**

Résultat du run GitHub Actions `42308538451` (commit `329a576`, code identique à la tête de branche) :

| | |
|---|---|
| Build `assembleNomapNonalyticsFullDebug` | ✅ succès |
| Tests unitaires | ✅ 510 tests, 0 échec, 0 ignoré (dont 19 nouveaux : 7 + 4 + 8) |
| APK | `MyAIDrive-Claude-debug.apk`, SHA-256 `613364e550007eaf6712c561e358aa983c35c97e3d2993b6164104ae28c52b57` |
| Manifeste fusionné (décodé) | package `com.myaidrive.claude` ; providers `com.myaidrive.claude.cardata.provider` et `com.myaidrive.claude.androidx-startup` uniquement (la seule mention `io.bimmergestalt.cardata.provider` est un `<queries>`, pas une déclaration) ; `DashboardAppService` présent |
| Signature | `CN=Android Debug` (clé jetable : aucun secret de keystore configuré, voir §5) |

## 8. À valider dans la BMW (non vérifiable ici)

- [ ] L'app se connecte (BMW Connected/My BMW installé, édition active seule).
- [ ] Notifications, lecture vocale, calendrier, assistants : identiques à AAIdrive officiel.
- [ ] Après accès aux notifications : applis musicales actives dans l'app et listées dans iDrive.
- [ ] Spotify (avec Client ID enregistré) : titres, pochettes, playlists, navigation et commandes iDrive.
- [ ] **My AI Drive** apparaît dans *Informations véhicule* et s'ouvre.
- [ ] Les six valeurs correspondent au combiné d'instruments (unités °C, km/h, L) ; `--` pour une valeur non transmise.
- [ ] La molette change de thème ; retour iDrive normal ; écran partagé (barre latérale) non masquant.
- [ ] Fluidité acceptable en Bluetooth ; pas de surchauffe/batterie anormale.
- [ ] Batterie : la valeur CDS `sensors.battery` correspond bien à la charge 12 V (à confirmer sur F34).
- [ ] Mode démo : bandeau « DÉMO » visible dans la voiture.
- [ ] L'interrupteur d'édition empêche bien toute connexion de l'édition inactive.
