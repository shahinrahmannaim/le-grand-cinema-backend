# Charge : scénario préparé, aucune mesure revendiquée

Avec k6 installé, sur un environnement de test autorisé :

```bash
k6 run -e API_BASE_URL=http://localhost:8080/api performance/catalogue.js
```

La cible proposée monte jusqu’à 100 utilisateurs virtuels, avec p95 < 500 ms et moins de 1 % d’erreurs HTTP. Il s’agit d’hypothèses du dossier, pas de résultats mesurés. Ce scénario mesure uniquement la lecture du catalogue ; il ne démontre pas la capacité du parcours de réservation ni celle d’un festival. Les 20 confirmations concurrentes du test JUnit valident un invariant transactionnel, pas un débit garanti. Tester séparément les parcours authentifiés et les connexions SSE avant d’affirmer un niveau de service. Ne pas exécuter cette charge sur une offre gratuite publique sans examiner ses quotas et les conditions du fournisseur.
