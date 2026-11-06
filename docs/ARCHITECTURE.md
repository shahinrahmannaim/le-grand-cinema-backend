# Architecture et décisions

Deux dépôts, un frontend Angular et une API Spring Boot. PostgreSQL est l'autorité du stock.
Java 21, Spring Boot 4.1.2, Maven Wrapper. PostgreSQL 17 en local, en CI et cible cloud compatible.
Les versions proviennent des générateurs officiels, avec versions stables figées dans les fichiers de build.

Le passage du frontend servi par Spring Boot (proposition Bloc 1) à deux déploiements est une
décision de réalisation demandée le 6 novembre 2026. Il nécessite une configuration explicite de
l'origine API, de CORS et du transport de session. La stratégie d'authentification doit être vérifiée
sur les vrais domaines de déploiement avant toute mise en service : un cookie entre deux sites
différents peut être bloqué par le navigateur. Ne pas désactiver CSRF pour contourner ce problème.

Les tables couvrent utilisateurs, films, salles, places, séances, réservations et affectations.
Les clés étrangères composites interdisent une place d'une autre salle ou une séance différente
de celle de la réservation. L'index unique partiel protège les affectations actives.
La contrainte d'exclusion interdit les horaires qui se chevauchent dans la même salle,
en tenant compte de la fin de rotation. Les écritures futures doivent aussi vérifier la durée du film.

La confirmation devra vérifier les droits, appliquer l'idempotence par acteur et écrire tous les
sièges dans une transaction ; cet index seul n'implémente pas US06. Une annulation devra libérer
les affectations et changer le statut dans la même transaction. Aucun endpoint d'annulation n'est exposé.

Deux Premium de 300 places, quatre Standard de 180, deux VIP de 120 et deux Événement de 70
donnent 1 700 sièges. Le plan proposé de dix sièges par rangée reste une hypothèse à valider.

Les migrations du profil demo contiennent exclusivement des données fictives. Les comptes et
opérations privées ne sont pas encore implémentés. Il n'existe aucune validation de charge ou
garantie de haute disponibilité à ce stade.
