# FTP-joc - Installation

## Installation

1. chargez le APK sur votre tel
2. il va demander à s'installer
3. Cliquer sur Install...
4. valider toutes les autorisations (inspectez le code si vous n'avez pas confiance mais je ne collecte rien, au départ, c'est un app perso pas de pub pas de traquers)

## Mise à jour

Remplacez simplement le APK par une nouvelle version puis réinstallez-la.

## Usage

Encore un outil pour fainéants

En vérité, il y a une vrai motivation, Gdrive retouche les photos et efface les tags EXFI ce qui est pas top pour la photogrammétrie
et puis un truc quand on bouge des centaines de photos, c'est la sélection
bref, une petite app qui gère un transfert vers un FTP, j'ai pris la précaution de pas sauver les identifiants en dur (EncryptedSharedPreferences pour les intimes) donc même si c'est une app perso tout le monde peut l'utiliser, il faut rentrer ses identifiantes une fois et après c'est la sécurité du tel qui le stocke

j'ai implémenté les boutons **shift** et **ctrl** pour la sélection (je comprend pas pk aucune app de transfert de photo ne le fait)
Bien sur, **Go** envoie la sélection sur le FTP mémorisé (nota, beaucoup de providers offrent ce service, il suffit de l'activer)

j'avais idée de pouvoir faire le ménage une fois les transferts confirmés, mais à vous de me dire sur le bouton **supp**rimer marche. Il fonctionne sur le pixel7 du simulateur, mais pas sur mon Xiaomi soit hyeprOS me les casse soit c'est une trop vielle version, d'android (10) 


à l'opuverture un appercu des dernières photo prises 

![0](http://joch04.free.fr/qta-php/images/ftp-joc/ouverture.jpg)

une fois la sélection faite on peut l'envoyer avec GO. Le shift permet de sélec là 1ere et la dernière de la série

![1](http://joch04.free.fr/qta-php/images/ftp-joc/selection.jpg)

Les settings où on met son adresse et dossier FTP (c'est appelé au 1er GO si vous ne l'avez pas renseigner)

![2](http://joch04.free.fr/qta-php/images/ftp-joc/connection.jpg)

