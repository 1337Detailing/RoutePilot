# Routix

Routix est une application Android de tournées terrain centrée sur trois usages : enregistrer une tournée GPS réelle, conserver des repères métier (`Marche arrière` et `2 côtés`) et rejouer la tournée en étant guidé sur la trace originale.

## Routix 2.1

- Interface sombre glass / iOS-inspired
- Onboarding de première ouverture avec consentement explicite
- Carte vectorielle MapLibre / OpenFreeMap, deux styles clair et sombre, sans clé API
- Cartes vectorielles françaises hors ligne téléchargeables et activables
- Enregistrement GPS précis avec statistiques en temps réel
- Repères géolocalisés `Marche arrière` et `2 côtés`, notes par appui long
- Pause, annulation du dernier repère et brouillon de secours automatique
- Historique des tournées, favoris, renommage, duplication, suppression et partage GPX
- Plan imprimable A4 : images PNG partageables, PDF et impression Android
- Noms de rues OpenStreetMap, changements numérotés dans l’ordre, départ/arrivée et repères métier
- Relecture guidée sur la trace enregistrée, progression, distance restante, prochain repère et détection hors trace
- Import GPX

Le projet Android actif est dans `RoutixStandalone/`.

## Build

GitHub Actions construit automatiquement l'APK debug avec `.github/workflows/build-debug-apk.yml`.

## Cartographie

Routix utilise MapLibre / OpenFreeMap en ligne et Mapsforge avec un adaptateur osmdroid hors ligne. Les packs de cartes sont téléchargés séparément de l'APK afin de ne pas gonfler artificiellement l'application.

## Plans imprimables

« Voir le plan » génère un atlas A4, avec le tracé GPS original et des flèches. Les changements de rue sont estimés par proximité et orientation avec les voies OSM ; les correspondances ambiguës ou absentes sont signalées et doivent être vérifiées avant distribution. La numérotation conserve les retours dans une même rue. Les pages se recouvrent à leurs limites.

La première génération nécessite Internet et demande confirmation avant d’envoyer l’emprise géographique à Overpass. Les rues sont mises en cache localement pendant sept jours. Les images et le PDF sont partagés uniquement sur demande. Limites : 100 000 points, emprise de 100 km², 100 pages. Les tests unitaires de géométrie sont exécutés avant la construction de l’APK.

## Stabilité et refonte

Le [rapport technique](RoutixStandalone/AUDIT-2.1.md) décrit les corrections, les fichiers, les changements d’interface et les limites de validation. Le suivi actif utilise un service foreground et un journal SQLite. Pour transmettre un problème : Plus → Exporter le diagnostic.
