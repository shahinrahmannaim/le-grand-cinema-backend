# Le Grand Cinéma — API

Projet Studi Bloc 3. Java 21, Spring Boot 4.1.2, Maven Wrapper, PostgreSQL 17 et Flyway.
Frontend : https://github.com/shahinrahmannaim/le-grand-cinema-frontend
Backlog : https://trello.com/b/3xFeH8OO/le-grand-cinema-projet-bachelor

## État réel de ce premier incrément

Le socle et le catalogue public (US01/US02) sont implémentés. Les comptes, la sélection des
places, la confirmation, les billets, le guichet et la gestion ne sont pas encore disponibles.
Ce premier incrément ne constitue pas le projet complet à soumettre. Aucune application cloud
ni validation métier n'est encore revendiquée.

## Démarrer sur macOS ou Linux

1. Ouvrir le dossier dans IntelliJ, importer `pom.xml` et choisir le JDK **21**.
2. Copier `.env.example` vers `.env` et conserver ce fichier uniquement en local.
3. Démarrer Docker Desktop, puis dans le terminal du backend :

```bash
cp .env.example .env
docker compose up -d --wait
```

Ne pas refaire `cp` si un `.env` personnalisé existe déjà.

4. Dans la configuration Run IntelliJ, définir :

```text
DB_URL=jdbc:postgresql://localhost:5433/legrandcinema
DB_USERNAME=cinema
DB_PASSWORD=local-development-change-me
SPRING_PROFILES_ACTIVE=demo
```

La valeur `DB_PASSWORD` doit être identique au `POSTGRES_PASSWORD` du `.env`. Le mot de passe
ci-dessus sert seulement au développement local. Ne pas l'utiliser pour le déploiement public.
Le port 5433 évite le conflit avec d'autres PostgreSQL sur 5432. Si le port du Compose change,
modifier aussi `DB_URL`. Spring Boot ne lit pas automatiquement le `.env` de Docker Compose.

5. Lancer `CinemaBackendApplication`, ou depuis le terminal :

```bash
DB_PASSWORD=local-development-change-me SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
```

Sur Windows utiliser `mvnw.cmd` et renseigner les mêmes variables dans IntelliJ.
Les migrations créent dix salles et 1 700 sièges. Le profil `demo` ajoute trois films fictifs
et trente jours de séances à partir de la première migration. Ces données ne se régénèrent
pas à chaque démarrage : actualiser les séances si la démonstration a lieu plus tard.
Ne jamais modifier une migration déjà appliquée : créer une nouvelle version.

## Vérifier le fonctionnement

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/catalog/films
```

Consulter les séances avec l'identifiant renvoyé par le catalogue :
`GET /api/catalog/films/{filmId}/screenings?date=2026-11-07`.
La date doit correspondre aux séances présentes dans la base. Les journées suivent le fuseau
Europe/Paris, y compris les changements d'heure. Les séances passées et annulées sont exclues.
Le contrat actuel se trouve dans [docs/openapi.yaml](docs/openapi.yaml).

## Tests et qualité

```bash
./mvnw verify
docker compose exec postgres createdb -U cinema cinema_test
DB_URL=jdbc:postgresql://localhost:5433/cinema_test DB_PASSWORD=local-development-change-me ./mvnw -Pdatabase-tests verify
```

La première commande exécute les tests unitaires et Checkstyle, sans base.
Les deux dernières commandes créent une base isolée puis exécutent les tests d'intégration.
Ne pas relancer `createdb` si cette base existe déjà. Les tests utilisent un vrai PostgreSQL via `DB_URL`.
Ils s'exécutent dans des transactions annulées après chaque test. Utiliser une base de test,
jamais une base de production. La CI utilise un service PostgreSQL isolé.
Rapports : `target/surefire-reports`, `target/failsafe-reports`, `target/site/jacoco/index.html`.
La couverture rapporte uniquement le code effectivement exercé ; aucun seuil arbitraire
ne remplace la vérification des invariants. La CI est configurée, son exécution distante reste
à vérifier après le premier push.

## Git et revue

- `main` : versions validées pour livraison.
- `develop` : intégration des incréments.
- `feature/usXX-description` : une histoire du Trello.
- `feature/project-foundation` : configuration et modèle initiaux.
- `fix/description` : correction tracée avec non-régression.

Ouvrir une pull request vers `develop` pour chaque fonctionnalité, puis relier carte Trello,
tests et documentation. Ne pas fabriquer une revue, une PR ni des résultats de CI.

## Déploiement prévu

Render construit le Dockerfile depuis ce dépôt. PostgreSQL reste externe, sur Neon.
Renseigner `DB_URL` (URL JDBC avec TLS, par exemple `...?sslmode=require`), `DB_USERNAME`,
`DB_PASSWORD`, `ALLOWED_ORIGINS` et, pour les seules données fictives, `SPRING_PROFILES_ACTIVE=demo`.
Neon doit autoriser l'extension `btree_gist` nécessaire à la contrainte de programmation.
Le plan gratuit Render peut dormir et n'offre pas une disponibilité continue.
Ne pas déployer PostgreSQL à l'intérieur du conteneur de l'API.

Le Dockerfile produit un runtime Java non privilégié. Les mots de passe sont fournis à
l'exécution, jamais dans l'image. Une configuration de déploiement n'est pas une preuve
que l'application est effectivement en ligne.

Voir [les décisions d'architecture](docs/ARCHITECTURE.md) et
[la traçabilité des histoires](docs/STORIES.md).
