# Parte 2: baseline e medicoes

## Antes de alterar a instalacao

- [ ] Recuperar o repositorio Git original e verificar branch, commit, remoto e alteracoes pendentes.
- [ ] Rever ficheiros rastreados e nao rastreados, incluindo segredos, antes de qualquer commit.
- [ ] Registar commit, regiao, plano Functions, configuracao Cosmos, dataset, imagens, maquina cliente e hora UTC.
- [ ] Registar o tamanho dos ficheiros de imagem, carga, duracao, repeticoes e cold/warm starts.
- [ ] Medir p50, p95, p99, throughput, requests, erros e HTTP 429.
- [ ] Medir p50, p95 e p99 de `POST:/media`, `GET:/media` e `POST:/auction/*/bid`.
- [ ] Registar Request Units, normalized RU consumption e throttling no Azure Monitor, para o mesmo intervalo UTC.
- [ ] Guardar os dados e resultados locais num local privado; `users.data` contem passwords.

A copia atual nao contem `.git`, pelo que nao ha commit, branch ou remoto a identificar aqui. O repositorio remoto encontrado estava vazio. Antes de entregar, o grupo deve confirmar o repositorio correto e publicar o codigo revisto sem segredos. Depois de rever `git status --short --branch`, `git diff` e os ficheiros nao rastreados, os comandos seguintes sao exemplos; adicionar explicitamente apenas ficheiros revistos e sem segredos:

```powershell
git add -- <ficheiros-revistos>
git commit -m "Complete part 1"
git tag part1-final
git switch -c part2
git push -u origin part2
git push origin part1-final
```

Nao criar o commit se nao houver alteracoes pendentes; nesse caso validar que o commit atual representa a Parte 1 antes de criar a tag. Conferir o nome do remoto com `git remote -v`. Nunca adicionar state Terraform, chaves, `local.settings.json` ou ficheiros de passwords; ignorar um ficheiro nao remove automaticamente um ficheiro ja rastreado.

## Preparacao

Executar nesta pasta. Sao necessarios Node.js, npm, Artillery e o plugin `artillery-plugin-metrics-by-endpoint`, alem das dependencias de `package.json`. Instalar apenas depois de verificar versoes e requisitos da ferramenta no ambiente usado para a experiencia.

O target guardado nos YAML e da app web, nao necessariamente da Function App. Obter o URL Functions no diretorio `cc-proj-fun` com `terraform output -raw function_url`; verificar que termina em `/rest`. Os exemplos abaixo substituem o target com `--target` e nao publicam nem alteram infraestrutura:

```powershell
$env:TARGET = '<URL-functions-com-rest>'
New-Item -ItemType Directory -Force results/baseline | Out-Null
npx artillery run --target $env:TARGET --output results/baseline/workload.json workload1-fun.yml
npx artillery report --output results/baseline/workload.html results/baseline/workload.json
```

Os cenarios criam auctions, bids e respostas, pelo que a execucao altera o dataset. Usar dados de teste autorizados, comparar datasets equivalentes e anotar as alteracoes. `workload1-light-fun.yml` e uma alternativa de carga menor; manter o mesmo ficheiro e parametros em cada comparacao. Existem tambem `create-users.yml` e `create-auctions.yml`, mas nao versoes Functions dedicadas: definir `FUNCTIONS=1` para que o processor envie os uploads como `application/octet-stream`. Criam utilizadores e auctions; `test-utils.js` guarda as credenciais geradas em `users.data`. Nao executar os preparadores sobre dados de producao.

Autenticacao: `POST /user/auth` devolve `Set-Cookie: cc:session`; o cliente do workload deve preservar o cookie entre pedidos do mesmo utilizador virtual. Confirmar com uma execucao controlada e os codigos de resposta, sem registar passwords ou cookies.

## Interpretacao

O JSON e HTML do Artillery contem contagens, throughput, erros e percentis; consultar tambem as metricas por nome de pedido do plugin. Confirmar que cada endpoint tem amostras suficientes: o workload e aleatorio e pode nao produzir uploads ou bids em todas as repeticoes. HTTP 429 do cliente nao substitui os throttles internos do Cosmos, que o SDK pode voltar a tentar.

No Azure Monitor da conta Cosmos, selecionar o mesmo intervalo UTC e filtros de conta, base e container, quando disponiveis. Recolher **Total Request Units**, **Normalized RU Consumption** e **Total Requests** por status 429; indicar a agregacao, intervalo e filtros usados. Consultar tambem Application Insights para falhas da Function. Nao somar percentagens nem inferir RUs por endpoint a partir de totais da conta partilhada.

Registar para cada execucao: variante, commit, target, regiao, dataset, tamanhos, carga, duracao, repeticao, inicio/fim UTC, p50/p95/p99 por operacao, throughput, requests, erros, 429, RU, normalized RU e observacoes. Usar `N/A` quando nao houver amostras ou metricas. Comparar baseline e Blob, Session e Strong e posterior politica personalizada mantendo carga, cliente, regiao, app e dados equivalentes. Cold starts, cache, rede e throttling podem alterar os resultados.

Pastas sugeridas: `results/baseline`, `results/blob`, `results/consistency-session`, `results/consistency-strong`, `results/consistency-custom`, `results/bids-before` e `results/bids-after`. Criar cada pasta apenas quando existir uma execucao; nao inventar resultados.

## Infraestrutura

Nao executar `terraform apply`, `terraform destroy` ou deploy automaticamente. Cada diretorio Terraform possui o seu proprio state. A configuracao Functions pode criar Cosmos partilhado ou apenas referenciar o criado pelo modulo web; destruir o estado que o gere pode apagar dados de ambos. Inspecionar o plano e fazer backup verificavel antes de qualquer destruicao. A configuracao atual cria Blob e permite escolher a consistencia apenas quando gere a conta Cosmos; consultar `PARTE2.md` antes de alterar recursos.
