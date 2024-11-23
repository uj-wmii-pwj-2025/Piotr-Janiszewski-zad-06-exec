## Zadanie do wykładu 6 - Własna implementacja `ExecutorService`

Należy zaimplementować klasę `MyExecutorService` w taki sposób, aby realizowała API `ExecutorService`.

Implementacja może być oparta na jednym wątku (single thread executor). Nie należy używać istniejących implemetacji, należy oprzeć się tylko na niskopoziomowym API wątków! Można używać istniejących implementacji interfejsu `Future`, np `CompletableFuture`.

Dodatkowo, należy dostarczyć testy, które przetestują wszystkie metody. Kilka przykładowych testów jest już zaimplementowanych w klasie `ExecServiceTest` - można się na nich wzorować.

### UWAGA
W tym zadaniu nie wystarczy "przejść" pipeline'a - konieczna analiza kodu.
