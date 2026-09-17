# RoutePilot

RoutePilot est une application Android de tournées terrain centrée sur trois usages : enregistrer une tournée GPS réelle, conserver des repères métier (`Marche arrière` et `2 côtés`) et rejouer la tournée en étant guidé sur la trace originale.

## V1

- Interface sombre glass / iOS-inspired
- Onboarding de première ouverture avec consentement explicite
- Carte OpenStreetMap en ligne sans clé API
- Cartes vectorielles françaises hors ligne téléchargeables et activables
- Enregistrement GPS précis avec statistiques en temps réel
- Repères géolocalisés `Marche arrière` et `2 côtés`, notes par appui long
- Pause, annulation du dernier repère et brouillon de secours automatique
- Historique des tournées, favoris, renommage, duplication, suppression et partage GPX
- Plan de tournée ordonné avec départ, arrivée et timeline des repères
- Aperçu automatique de la tournée
- Relecture guidée sur la trace enregistrée, progression, distance restante, prochain repère et détection hors trace
- Import GPX

Le projet Android actif est dans `RoutePilotStandalone/`.

## Build

GitHub Actions construit automatiquement l'APK debug avec `.github/workflows/build-debug-apk.yml`.

## Cartographie

RoutePilot utilise osmdroid/OpenStreetMap pour la carte en ligne et Mapsforge pour les cartes vectorielles hors ligne. Les packs de cartes sont téléchargés séparément de l'APK afin de ne pas gonfler artificiellement l'application.
