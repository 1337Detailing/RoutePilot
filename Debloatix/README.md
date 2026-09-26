# Debloatix

Debloatix est un gestionnaire d'applications Android orienté debloat, piloté par Shizuku, avec une interface inspirée du design Catppuccin de Routix.

## Fonctionnalités

- Liste et recherche des applications installées
- Filtres système / utilisateur
- État Shizuku et demande de permission
- Désactivation / réactivation d'une application système
- Désinstallation d'une application système pour l'utilisateur Android courant
- Restauration via `cmd package install-existing`
- Historique local des packages retirés
- Désinstallation Android standard pour les applications utilisateur
- Garde-fous sur les composants critiques Android
- Interface Catppuccin Mocha issue du site Routix

## Sécurité

La désinstallation Shizuku utilise `pm uninstall --user 0`. Pour une application système, l'APK système n'est pas effacé de la partition système et peut généralement être restauré avec `cmd package install-existing --user 0`.

Debloatix bloque volontairement les actions destructrices sur une petite liste de composants Android critiques. Cette liste n'est pas exhaustive : un package système peut rester indispensable au constructeur.

## Build

```bash
gradle :app:assembleDebug
```

L'APK se trouve dans `app/build/outputs/apk/debug/app-debug.apk`.

## Shizuku

Debloatix utilise l'API officielle Shizuku et un UserService pour exécuter les commandes de gestion de packages avec l'identité shell/root fournie par Shizuku.
