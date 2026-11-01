# Projeto 1, Parte 2

## Antes de executar

O pacote contem `baseline/`, com o codigo original da Parte 1, e `part2/`, com as alteracoes. Nenhum state, chave, configuracao local ou ficheiro `users.data` deve ser partilhado. Usar apenas uma subscricao e dados de teste autorizados. Os testes de carga e de concorrencia criam dados e podem gerar custos.

Medir primeiro a Function App da Parte 1, antes de a substituir. Guardar os resultados reais; o codigo da baseline esta em `baseline/`, mas o state Terraform original deve ser recuperado em seguranca pela pessoa que criou os recursos. Nao se pode gerir ou destruir recursos existentes com um state vazio. A baseline e a versao final incluem varias diferencas: comparacoes entre elas nao isolam automaticamente o efeito de cada alteracao.

## Infraestrutura

Requisitos: Java 21, Maven, Terraform >= 1.5, Azure CLI autenticada, Node.js >= 22.18 e Artillery 2. A configuracao em `part2/cc-proj-fun` cria um container privado Blob `media` na Storage Account da Function e passa a connection string atraves de `BlobStoreConnection`. O state contem segredos; nunca o enviar ou colocar no Git.

Se os recursos da Parte 1 existirem, primeiro planear e rever a sua remocao usando os states originais: normalmente destruir primeiro `cc-proj-fun` e depois `cc-proj`. Isto apaga recursos e dados, incluindo o Cosmos da Parte 1. Confirmar propriedade, backups e dependencia da aplicacao web antes de executar; nao assumir que o pacote contem os states. Uma subscricao apenas suporta uma conta Cosmos free tier.

Para criar uma infraestrutura independente de Parte 2, em `part2/cc-proj-fun`, preencher um ficheiro local `terraform.tfvars` a partir de `terraform.tfvars.example`, escolhendo `region`, `suffix` e `create_shared_resources = true`. Se o resource group ja existir, ajustar `create_resource_group`. Nao duplicar recursos geridos por outro state. Com `create_shared_resources = false`, a conta e gerida por outro modulo e `cosmos_consistency` nao a altera.

```powershell
terraform init
terraform fmt -check
terraform validate
terraform plan -out=part2.tfplan
# Rever o plano e os custos antes de executar:
terraform apply part2.tfplan
terraform output
mvn test
mvn package
```

O plano pode conter dados sensiveis: nao partilhar `part2.tfplan`. Terraform gera `.mvn/maven.config` com os nomes reais da app e resource group. Fazer deploy apenas apos revisao, com `mvn azure-functions:deploy`. Para remocao futura, primeiro rever `terraform plan -destroy` e depois, apenas quando autorizado, `terraform destroy` no state correto.

Os defaults sao Cosmos Session e `consistency_mode = "account"`. Para comparar, planear separadamente `-var="cosmos_consistency=Session" -var="consistency_mode=account"`, `-var="cosmos_consistency=Strong" -var="consistency_mode=account"` e `-var="cosmos_consistency=Strong" -var="consistency_mode=custom"`. Aplicar apenas depois de rever cada plano e reiniciar a Function App para carregar a configuracao atual. A mudanca de consistencia normalmente atualiza a conta, mas o plano real e decisivo; Strong depende da configuracao Azure e pode ter maior custo/latencia. Confirmar o nivel no portal Cosmos DB. No modo custom, uma conta externa tambem tem de usar Strong.

Imagens que ja existam no Cosmos nao sao migradas automaticamente. Para dados antigos e necessario migra-las preservando o id e o content type, ou recriar o dataset; nao apagar os documentos antigos antes de verificar a migracao. Os testes nao devem comparar um dataset com imagens ausentes.

## Testes

Em `part2/cc-proj-test`, instalar dependencias e preparar dados de teste. Configurar TARGET para o output `function_url`, incluindo `/rest`. Os preparadores originais usam `image/jpeg`; `FUNCTIONS=1` altera apenas os seus uploads para bytes. `users.data` contem passwords e nao deve ser enviado.

```powershell
npm install
npm install --no-save artillery artillery-plugin-metrics-by-endpoint
$env:TARGET = '<function_url>'
$env:FUNCTIONS = '1'
npx artillery run --target $env:TARGET create-users.yml
npx artillery run --target $env:TARGET create-auctions.yml
node test-media.js
New-Item -ItemType Directory -Force results/baseline, results/blob, results/bids-before, results/bids-after | Out-Null
npx artillery run --target $env:TARGET --output results/blob/workload.json workload1-fun.yml
npx artillery report --output results/blob/workload.html results/blob/workload.json
$env:ROUNDS = '10'; $env:CONCURRENCY = '10'
node concurrent-bids.js | Out-File -Encoding utf8 results/bids-after/concurrency.json
```

Usar estes mesmos scripts contra a app original para guardar `results/baseline` e `results/bids-before` antes de mudar a instalacao. A concorrencia exige pelo menos `CONCURRENCY` utilizadores diferentes em `users.data`; executa 10 leiloes novos por defeito, produz estados, respostas e tempos, e retorna erro quando o vencedor difere do maior bid aceite ou falta um bid aceite. `403` pode ser esperado para um bid ultrapassado; `409` indica contenção apos tres tentativas. O teste tem de ser repetido porque a corrida pode nao ocorrer sempre.

Para regressao adicional, compilar o projeto Functions e executar `java -cp target/classes cc.utils.TestBackend <function_url>` a partir de `part2/cc-proj-fun`. O teste cria e elimina um utilizador de teste e altera outros dados; inclui imagem de 3 MB. Verificar os resultados HTTP, cookies e paginacao. `mvn test` executa testes locais sem Azure.

Para medir query contra point read, configurar localmente `COSMOSDB_URL`, `COSMOSDB_KEY` e `COSMOSDB_DATABASE` sem os colocar no codigo, e executar no modulo Functions:

```powershell
mvn -q exec:java "-Dexec.mainClass=cc.utils.ReadComparison" "-Dexec.args=auctions ID_DO_AUCTION ID_DO_AUCTION 30" > read-comparison.csv
```

Escolher um documento existente, fornecendo id e partition key corretos. O diagnostico usa uma query parametrizada na mesma particao e o mesmo default de consistencia do point read; regista tempos, documentos e RU por iteracao, somando as paginas da query. O CSV e a ordem fixa das operacoes podem conter enviesamentos de aquecimento; repetir, registar o contexto e nao publicar identificadores sensiveis.

## Consistencia por operacao

No modo `account`, todas as leituras seguem o default da conta. No modo `custom`, a conta tem de ser Strong: as escritas continuam a obedecer a politica da conta; as opcoes abaixo aplicam-se apenas as leituras. Cosmos nao permite elevar o `ConsistencyLevel` acima do default da conta. Leituras Session em instancias Functions diferentes nao partilham automaticamente os tokens; para read-your-writes entre instancias seriam necessarios tokens propagados ou Strong.

| Operacao | Acesso, container e particao | Custom | Motivo |
| --- | --- | --- | --- |
| Criar user | create users `/id` | conta | Escrita |
| Obter/atualizar/eliminar user; autenticar | point read users `/id`, replace/delete | Strong | Evitar credenciais ou perfil desatualizados entre instancias |
| Criar/validar sessao | create/point read sessions `/id` | Strong na leitura | Evitar validacao desatualizada; a expiracao de sessoes no servidor nao foi alterada |
| Listar auctions de user | query auctions `/id` | Session | Lista ligada a alteracoes do utilizador |
| Criar auction | create auctions `/id` | conta | Escrita |
| Obter/atualizar auction | point read/replace auctions `/id` | Session | ETag impede substituir uma versao diferente; Session nao garante tokens entre instancias |
| Recent/about-to-close/popular/search | query auctions `/id`; popular conta bids `/auctionId` | Eventual | Listagens informativas toleram atraso |
| Criar bid | point read/replace auctions `/id`; create/delete bids `/auctionId` | Session na leitura | ETag, nao consistencia, controla a corrida |
| Listar/obter bids | query/point read bids `/auctionId` | Session | Lista e detalhe de atividade |
| Criar/listar/responder questions | read auction, create/query/point read/replace questions `/auctionId` | Session | Atividade e resposta |
| Upload/download media | Blob `media`, nome hash | N/A | Nao usa Cosmos |

Eventual e escolhido para listas informativas; Consistent Prefix e Bounded Staleness foram considerados, mas nao sao necessarios para essas listas. Strong e usado para autenticacao no modo custom. Session e mais fraco, mas requer propagacao de tokens para garantir a mesma sessao entre instancias.

## Concorrencia e limitacoes

O bid e criado em `bids`; o auction e lido por id e atualizado com If-Match na ETag. Uma resposta 412 provoca nova leitura e validacao, no maximo tres tentativas. Um bid ultrapassado devolve 403, e esgotar tentativas devolve 409. `AuctionOps.update` tambem usa ETags para nao apagar um vencedor concorrente. Os logs indicam conflitos, mas nao incluem passwords.

Os documentos estao em containers diferentes: nao existe `TransactionalBatch` entre eles. A implementacao tenta apagar o bid quando uma rejeicao e conhecida; durante a operacao ele pode ser visivel. Se houver crash, falha na compensacao ou resultado de escrita incerto, pode restar um bid nao confirmado; nesse caso e necessaria reconciliacao manual. Uma resposta incerta nao apaga o bid, para evitar que um auction aponte para um documento removido. Assim, a atomicidade completa e a ausencia de inconsistencias em falhas nao estao garantidas; seria necessario redesenhar o armazenamento ou criar um processo de reconciliacao duravel. Outras aplicacoes que escrevam auctions sem ETag tambem podem contornar a protecao. Nao executar escritas concorrentes da app web original durante o teste.

## Medicoes e custos

Ver `cc-proj-test/BASELINE.md`. Para cada execucao registar inicio/fim UTC, cliente, regiao, app, carga, dataset, tamanho das imagens e warm/cold start. Artillery: p50/p95/p99 por `POST:/media`, `GET:/media` e `POST:/auction/*/bid`, throughput, requests e erros. Azure Monitor Cosmos: Total Request Units, Normalized RU Consumption e Total Requests filtrado por 429; indicar intervalo e agregacao. Comparar pelo menos tres repeticoes equivalentes e considerar cache, rede, cold starts e throttling. Contar conflitos e retries nos logs da Function, sem expor dados privados.

Custos parametrizados, com precos atuais da mesma regiao: armazenamento = numero de imagens x tamanho medio em GB x preco GB/mes; RU mensais = uploads x RU/escrita + downloads x RU/leitura + restantes operacoes x respetivas RUs, convertidas conforme a oferta provisionada ou serverless; Blob = armazenamento x preco GB/mes + uploads x preco/escrita + downloads x preco/leitura + retrieval e transferencia aplicaveis. A configuracao usa Cosmos throughput provisionado de 400 RU/s por defeito, free tier quando disponivel, Storage Standard LRS e plano Functions Y1; confirmar access tier e precos reais no Azure antes de calcular. Strong pode aumentar RU de leitura e latencia; nao assumir um valor sem medir.
