# Vérifications du premier incrément

Vérifications exécutées le 6 novembre 2026, sur Java 21 et PostgreSQL 17.6.

- `./mvnw -Pdatabase-tests verify` : compilation, Checkstyle, 5 tests unitaires et
  7 tests d'intégration réussis, aucun échec ni test ignoré.
- Migrations Flyway appliquées sur une vraie base PostgreSQL ; 10 salles et 1 700 places.
- Démarrage du JAR : santé UP, catalogue de 3 films fictifs et séances renvoyées par l'API.
- Couverture JaCoCo de cet incrément : 50 lignes couvertes sur 52, soit 96,15 %.
  Cette mesure porte sur le petit périmètre actuellement implémenté, pas sur les fonctions restantes.
- Frontend : 6 tests Vitest réussis et compilation Angular de production réussie.
  Couverture mesurée par le runner : 80,16 % des lignes instrumentées et 89,18 % des branches.

Les tests de base vérifient la capacité initiale, le refus de séances qui se chevauchent et
le refus d'un siège appartenant à une autre salle. Les tests HTTP contrôlent le catalogue,
les séances, les dates invalides et les films inexistants. Les tests unitaires contrôlent
notamment les changements d'heure et le jour local Europe/Paris.

Limites de vérification : le navigateur Chromium n'a pas pu démarrer dans l'environnement
de travail (restriction sur les sockets Unix). La vérification visuelle et les parcours navigateur
complets restent à réaliser sur la machine de développement et sur le déploiement.
Docker n'était pas disponible dans cet environnement ; le Dockerfile et Compose sont fournis
mais leur exécution doit encore être vérifiée. Les tests de base ont utilisé PostgreSQL 17 natif.

Les workflows GitHub sont préparés ; aucune exécution distante, pull request, validation métier
ou URL de déploiement n'est revendiquée tant que ces opérations n'ont pas réellement eu lieu.
L'authentification, la réservation transactionnelle et idempotente, les billets et l'administration
restent à développer et à tester. Les résultats présents ne valident pas ces fonctions futures.
