# Routix 2.1 — audit et refonte terrain

## Crash : constats et limites

La cause exacte du crash après vingt minutes nécessite son rapport. Les défauts observés dans les sources étaient :

- Absence de service foreground dans le manifeste ; l'activité détenait les enregistrements et le guidage.
- Plusieurs abonnements GPS indépendants (activité, overlay osmdroid, contrôleur MapLibre).
- Contrôleur MapLibre par réflexion, polling toutes les 500 ms, suppression/recréation de polylignes, masquage volontaire pendant le guidage.
- Écritures GPX sur le thread UI pendant l'enregistrement ; suppression du brouillon même après un échec de sauvegarde finale.
- Pas de persistance de la progression de guidage, exceptions ignorées, parseur GPX non configuré pour les espaces de noms des repères.
- Simplification récursive des traces longues, susceptible d'épuiser la pile.

Les corrections ne constituent pas une preuve de disparition du crash terrain. Un replay algorithmique de trente minutes n'émule pas trente minutes de rendu GPU ni les restrictions batterie d'un téléphone.

## Fichiers et composants

| Fichiers | Changement |
|---|---|
| `TrackingService.java`, `AndroidManifest.xml` | Un abonnement GPS sur le looper principal ; service foreground pendant enregistrement ou guidage ; notification ; observers détachés quand l'activité disparaît ; arrêt du GPS inactif ; reprise START_STICKY. Aucun wake lock permanent. |
| `SessionJournal.java` | Points et repères dans SQLite, progression enregistrée après chaque mise à jour. Écritures sérialisées hors UI. Reprise après recréation du processus. |
| `RouteStore.java`, `PersistentRouteBackup.java` | Brouillon conservé si la sauvegarde finale échoue ; lecture GPX avec namespaces et validation des coordonnées ; restauration des copies hors UI ; les suppressions explicites ne sont plus annulées au lancement. |
| `DiagnosticLog.java`, `RoutixApp.java`, `file_paths.xml` | Journal tournant borné (~1 Mio), handler d'exception qui délègue au handler Android, export manuel via FileProvider. |
| `RouteNormalizer.java` | Simplification itérative pour supprimer le risque de débordement de pile. |
| `ModernMapController.java` | Lifecycle explicite, aucun GPS propre, sources GeoJSON persistantes, flèches sur la portion à venir, repères, position et rotation lissée par le plus court angle. |
| `RoutixActivity.java`, `TerrainIcon.java` | Quatre onglets fixes ; contrôles simplifiés ; icônes homogènes ; réglages essentiels et Avancé replié ; export diagnostic ; sélection des packs. |
| `DepartureNavigation.java` | Estimation OSRM, choix Google Maps/Waze, repli navigateur et message explicite si le calcul est indisponible. Ce n'est pas un calcul d'itinéraire poids lourd. |
| `FranceOfflineManager.java` | Vérification du statut DownloadManager avant de proposer un fichier comme installé. |
| `OnboardingActivity.java` | Pages défilables, apparitions progressives, protection contre doubles clics, état sauvegardé, arrêt des animations infinies et notice de confidentialité mise à jour. |

La journalisation conserve chaque écriture SQLite terminée. Les toutes dernières écritures encore dans la file d'attente peuvent être perdues si Android tue brutalement le processus. Le brouillon GPX reste un secours toutes les quinze secondes. Un arrêt forcé Android ne permet pas de continuer à recevoir des positions pendant que l'application est arrêtée.

## Nouveaux styles

- `assets/maps/light.json` : clair minimaliste, routes blanches, peu de distractions.
- `assets/maps/dark.json` : fond sombre et labels assortis à l'interface mauve.
- `assets/maps/light.xml`, `dark.xml` : palette équivalente pour Mapsforge hors ligne.
- `MapStyles.java` : sélection jour/nuit et lissage angulaire.

[OpenFreeMap](https://openfreemap.org/) annonce un service sans clé et sans limite de vues/requêtes. Les styles locaux utilisent son schéma OpenMapTiles et conservent l'attribution. La disponibilité d'un service public n'est pas garantie. Il n'est pas nécessaire d'ajouter un fournisseur payant.

MapLibre ne lit pas les fichiers `.map` de Mapsforge : osmdroid est conservé comme adaptateur de rendu **hors ligne**, sans abonnement GPS ni téléchargement raster. La palette est cohérente, mais les deux moteurs ne sont pas identiques pixel par pixel. Les nouveaux styles GL devront être vérifiés sur téléphone, particulièrement sur les GPU et polices du Vivo.

## Écrans et fonctions

- **Carte** : vitesse, cap/nord, recentrage, flèches et portion restante, repère suivant, reprise au point courant. Le cap GPS est utilisé en mouvement ; à l'arrêt le dernier cap fiable est conservé.
- **Enregistrer** : démarrer, pause/reprise, terminer et sauvegarder, repères Marche arrière / 2 côtés.
- **Tournées** : recherche, favoris, renommage, duplication, suppression, partage GPX, import, plan A4 et bouton Aller au départ.
- **Plus** : heures, réglages, cartes hors ligne, diagnostic ; GPX Lab dans Avancé.
- **Réglages** : accent, deux fonds de carte, icône, trajet au départ, tolérance, rappels vocaux, écran allumé et nuit automatique. Les préférences techniques restantes sont masquées ou dans Avancé.

Fonctions simplifiées/supprimées :

- L'ancien `ClassicNavigationActivity` est remplacé par le trajet au départ : le guidage routier réel appartient à Google Maps/Waze.
- Humanitaire, CyclOSM, OpenTopoMap et l'option raster classique quittent le sélecteur.
- GPX Lab est déplacé, pas supprimé. Les heures restent disponibles sous Plus.
- Les réglages décoratifs de transparence/densité/vitesse d'animation ne sont plus exposés comme réglages terrain.

Ajouts utiles : rappels vocaux à moins de 55 m d'un repère (sans réseau si une voix française locale est installée), nuit automatique 20 h–7 h, diagnostic partageable. Une future commande « pause déchetterie / reprendre la collecte » pourrait éviter de confondre liaison et progression, mais n'est pas ajoutée sans validation métier.

## Vérification

Le workflow existant lance `gradle :app:testDebugUnitTest :app:assembleDebug`. Les tests couvrent les boucles, retours dans une même rue, reprise monotone, mauvais GPS, checkpoint SQLite, marqueurs GPX namespacés, rotation au nord et replay algorithmique de trente minutes avec restauration à vingt minutes. Les contrôles de layout couvrent les quatre onglets sur 320, 390 et 430 dp.

À vérifier sur appareil : collecte de 45–60 minutes, écran éteint, passage à Waze puis retour, perte réseau, GPS désactivé/réactivé, changement jour/nuit, pack Mapsforge terminé, rapport après interruption. Le contrôle GitHub Dependency Review échoue actuellement parce que le Dependency graph du dépôt est désactivé ; ce contrôle distinct n'a pas été retiré.
