# CI/CD — Roadmap para Onda 1 e além

## Onda 0 — CI mínima (pronta)

- ✅ Build + testes: `./mvnw clean install`
- ✅ ArchUnit: regras de arquitetura no `app/test`
- ✅ Secrets scan: Trufflehog detecta tokens expostos

## Onda 1 — CI de qualidade (planejado)

Quando os módulos tiverem cobertura mensurável:

- [ ] **JaCoCo** — relatório de cobertura com gate de 70% mínimo
  - Configurar no pom.xml: `jacoco-maven-plugin` + `maven-enforcer-plugin`
  - Cada commit deve manter ou melhorar a cobertura
  
- [ ] **SpotBugs** — análise estática de bugs
  - Configurar no pom.xml: `spotbugs-maven-plugin`
  - Falha o build se encontrar HIGH/CRITICAL
  
- [ ] **Trivy** — scan de vulnerabilidades em dependências
  - Já tem o script, só ativar quando tiver histórico de falsos positivos
  
- [ ] **Version sync** — pom.xml ↔ CHANGELOG.md
  - Criar CHANGELOG.md na raiz
  - Script em `.github/scripts/check-version-sync.sh` para validar na CI

## Onda 2+ — CI avançada (futuro)

- Performance benchmarks (se houver ponto de saturação)
- Sonarqube ou Code Climate (integração com GitHub)
- Cobertura por módulo (restaurant > hotel > billing)
- E2E com `.http` em container (se valer a pena)

---

**Regra:** CI não cresce sem necessidade. Cada ferramenta entra quando existe:
1. Um problema real que ela resolve (não antecipação)
2. Cobertura suficiente pra não dar falso positivo
3. Tempo de execução aceitável (build não pode ficar > 10 min)
