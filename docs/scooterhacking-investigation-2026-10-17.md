# Investigação ScooterHacking — 17 de outubro de 2026

## Resultado e limites

Existe material concreto para desenvolver funcionalidades equivalentes de diagnóstico,
configuração e registo de viagens. Não existe evidência de que copiar a interface ou
extrair o cliente permita reproduzir todos os serviços, obter todo o firmware ou
desbloquear qualquer modelo. A análise estática não substitui validação física.

Esta investigação não executou a ScooterHacking, não abriu sessões de conta, não
contactou scooters, não enviou comandos BLE e não pediu firmware a serviços de
elegibilidade. Não foram lidos o armazenamento seguro nem credenciais da instalação.
Não foi copiado código decompilado para a nossa app.

## Artefactos efetivamente examinados

| Artefacto | Identificação | Análise feita |
| --- | --- | --- |
| APK Android atual, obtido no canal release oficial | `sh.cfw.utility`, 4.2.1, build 78; 65 093 981 bytes | Manifesto e identidade via REA/JADX; inventário ZIP das bibliotecas |
| Instalação Windows existente | 4.2.1+78 | Versão PE; inventário Flutter; pesquisa estática de strings em `data/app.so` |
| APK Android anterior | `sh.cfw.utility.pre_release.open_beta`, 3.0+pre_release.open_beta (5) | Manifesto, 3653 classes; métodos selecionados de ligação e encaminhamento |
| `bootstrap.zip` de uma investigação anterior | Perfis zt3, f3, g3 e gt3 | Comparação de modelos, registos, controlos, máscaras e placas declaradas |
| Cliente web oficial | `utility.cfw.sh/web/main.dart.js`, 1 469 967 bytes | Download e pesquisa estática limitada; sem execução |
| MyNaviScooter atual | Código local | Política de escrita, baseline, leitura e descodificação |

O bootstrap anterior não foi demonstrado como sendo o bootstrap atual da 4.2.1.
Os seus controlos são candidatos históricos, não compatibilidade atual certificada.
O APK beta foi enviado para a Reciclagem após confirmação do APK substituto atual.
Os documentos e o bootstrap históricos foram conservados como referências, não como
versões executáveis para instalar ou base para ativar escritas.

### Impressões SHA-256

- APK atual: `0c93c4aa75d526e08ec377e4505d80a9bbd11508cab72900fb97508c8a54ec46`
- APK beta retirado: `0a163989e98c1245e983134347f01ca6e5076a76d5e3d0d6e71db3b2cc6275df`
- Windows `data/app.so`: `7f62aa4da4c7d0ed24775d0fb1f0983de427b03490fb0b88b09580b650423378`
- Bootstrap histórico: `4b09e32f9aa4e11a129c11a047c8a0830c9f29b33068bfbbdb48717b756bcf76`
- JADX headless 0.7.1: `6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75`

## Verificação da versão mais recente

O site oficial `https://utility.cfw.sh/` referencia a API pública
`https://apps-data.cfw.sh/utility/builds`. A resposta consultada nesta data indica
Android e Windows 4.2.1/build 78. O APK foi descarregado do URL release devolvido
por essa API e o manifesto foi conferido independentemente com REA/JADX.
O hash local identifica o ficheiro; não equivale a validação de assinatura do editor.

Não confundir a versão de um cliente com a do catálogo de modelos ou a do firmware.
Antes da próxima investigação deve consultar-se novamente a API, sem assumir que
4.2.1 continua a ser a mais recente. O script `scripts/check-shu-release.ps1` faz
essa consulta sem atualizar aplicações, aceder à conta ou apagar ficheiros.

## Ligação e autenticação

Observações diretas da beta, **não atribuídas automaticamente à versão 4.2.1**:

- `ScooterActivity.getBleRandom` constrói um pedido de pré-comunicação dirigido
  ao endereço Bluetooth definido pelo modelo, em vez de um endereço universal.
- `p1` cifra o pedido antes de o entregar ao serviço de transporte.
- `k` passa pela reconstrução da mensagem e pela seleção da família de protocolo;
  uma descodificação inválida é rejeitada antes do processamento funcional.
- `j1` distingue pré-comunicação, emparelhamento e autenticação; a confirmação
  do emparelhamento e a de autenticação não são o mesmo evento.
- A instrução para premir o botão físico está associada ao estado de protocolo.
  Um toque num diálogo do telefone não é tratado como confirmação da scooter.
- Só após resposta de autenticação bem-sucedida inicia a carga de dados e grava
  o aleatório de sessão para reconexão.

A MyNaviScooter já tem evidência de autenticação real nos relatórios do proprietário.
Não é necessário recomeçar o emparelhamento a cada ligação. As regras observadas
na beta não justificam substituir a implementação já validada por uma cópia antiga.

## Arquitetura atual: porque o JADX não extrai tudo

O APK 4.2.1 contém `libapp.so`, `libflutter.so`, `libdartjni.so` e `libintegrity.so`
para ARM64, ARMv7 e x86-64. A camada principal é Flutter AOT, não Java/Kotlin.
O JADX lê o manifesto e o DEX, mas não recompõe por si só a aplicação Dart original.
Não foi feita descompilação nativa completa nem estabelecida a finalidade de cada
biblioteca pela simples presença do nome.

Na versão Windows atual, as strings indicam carregamento de pacotes de modelo
com `model.json` e `ui.json`, cache validada e operações dependentes de serviços
externos. Existem referências a atualização do cliente, repositório de firmware,
serviço TEA, elegibilidade e configuração SHFW. Uma URL ou mensagem não prova
o contrato completo, a disponibilidade de um serviço ou autorização para reutilizá-lo.

## Diferenças X3 nos perfis históricos

| Modelo | Registos VCU declarados | Particularidades observadas |
| --- | --- | --- |
| ZT3 | 19 | Eco/Drive/Sport, arranque, KERS, luzes, aceleração e botão personalizado |
| F3 | 19 | Semelhante ao ZT3, mas declara também a opção `more_speed` |
| G3 | 20 | Declara `more_speed` e volume de efeitos sonoros |
| GT3 | 18 | Sport/Race partilham um campo; não deve receber o plano de escrita do F3 |

Os perfis distinguem placas VCU e BLE por modelo. Mesmo quando o offset coincide,
as máscaras, os limites apresentados e os modos disponíveis podem diferir.
O limite de um slider é uma regra da interface, não prova de aceitação do firmware.
`more_speed` não prova que a F3/G3 atual permita 30 km/h, nem que exista o mesmo
controlo na ZT3. Não se deve adicionar esse bit a modelos onde não foi confirmado.

Outros candidatos a controlos: unidades, sons de piscas, farol automático, luz
traseira, desligar automaticamente, alarme, controlo de tração e limite de carga.
Qualquer escrita precisa de identificação do modelo/firmware e teste independente.
KERS, aceleração, arranque sem impulso, tração e modos alteram comportamento de
condução: não são controlos cosméticos nem automaticamente seguros.

## Firmware, região e dependências

O binário Windows 4.2.1 contém mensagens para firmware que bloqueia mudanças de
serial e para ausência de alternativa compatível, bem como validação de modelo e
placa antes de transferir firmware. Isto contraria a hipótese de existir uma operação
universal de região/desbloqueio que basta expor na nossa app.

As mensagens são indícios estáticos de ramos do cliente: não demonstram que a
ZT3 do proprietário entra num ramo específico. Não foi identificado um método
validado para ultrapassar a restrição da VCU 1.5.9. Não foram obtidas imagens de
firmware nem ensaiados downgrade, alteração de serial ou modificação de região.

## Lacunas concretas na MyNaviScooter

1. `X3CapabilityPolicy.profileWrites` bloqueia atualmente todas as escritas. A
   interface avançada não significa funcionalidade validada para F3/G3/GT3.
2. `BaselineStore` guarda um único payload por instalação, não um por scooter.
   A segunda scooter com outro serial não pode obter uma baseline própria neste
   armazenamento. É necessário separar snapshots por identidade antes de suportar
   várias scooters de forma útil.
3. A baseline obrigatória cobre 14 registos. Não é um backup de toda a memória,
   de todo o firmware, de todos os módulos nem de todas as configurações.
4. Há código legado de restauro com valores especiais para testes antigos da ZT3.
   Está atrás da política que bloqueia escritas. Antes de ativar alterações é
   necessário eliminar inferências por serial e recuperar apenas o estado original
   comprovado daquela scooter, nunca um valor fixo presumido.
5. O plano de escrita atual só contempla dois campos de velocidade. Não restaura
   todos os 14 registos e não constitui uma implementação geral de configurações.
6. Leitura e descodificação continuam centradas na ZT3. Escalas, larguras de campos,
   quantidade de células e campos indisponíveis precisam de perfis específicos;
   receber dois bytes não demonstra que a interpretação esteja correta.

Estas conclusões descrevem o estado encontrado durante a investigação.

### Correções subsequentes neste projeto

- Baselines cifradas isoladas por serial, conservando a primeira captura e migrando
  a baseline antiga sem a substituir pelos valores da sessão atual.
- Snapshot danificado bloqueia substituição silenciosa; registos duplicados,
  hex inválido e comprimentos incoerentes são rejeitados.
- Retirada a recuperação por valores fixos do código de restauro; o restauro usa
  exclusivamente os dois campos da baseline dessa scooter.
- O plano de velocidade preserva o byte não alterado da baseline.
- Testes Android adicionados para isolamento, reabertura, migração e corrupção.

Estas correções não ativam a política de escritas nem convertem o snapshot de
registos num backup integral. A validação BLE continua dependente de hardware.

## Navegação e aparência

Não foi demonstrado um motor de navegação rodoviária reutilizável na ScooterHacking.
Termos como `navigationMode`, `route` ou `bottomNavigationBar` podem ser componentes
de navegação entre ecrãs do Flutter. As strings de viagens/CSV/histórico indicam
registo de viagens, não equivalência a um GPS com cálculo de percursos.

A navegação da MyNaviScooter deve continuar independente. O visual inspirado na
Segway pode ser construído com os nossos componentes; a equivalência de funções
oficiais precisa ainda do levantamento da app Segway completa. Não usar marcas,
logos ou interfaces copiadas como se a app fosse oficial.

## Sequência recomendada

1. Baseline multi-scooter, versionada e exportável, sem credenciais no ficheiro.
2. Catálogo próprio de capacidades por modelo, placa e firmware; desconhecido
   significa indisponível, não compatível por semelhança.
3. Ecrãs de informação, bateria, diagnóstico e histórico com origem e qualidade
   de cada valor; erros de leitura não devem aparecer como zeros reais.
4. Configurações individuais com leitura prévia, confirmação, escrita serializada,
   releitura, deteção de reversão e restauro do campo efetivamente guardado.
5. Só depois validar alterações de condução, em veículo parado e condições
   controladas, sem prometer um desbloqueio universal.
6. Completar o levantamento da Segway quando o telefone estiver disponível.

## Fontes e ferramentas

- REA 6.5.0 local; JDK Temurin 21; JADX headless 0.7.1 com hash verificado.
- `https://utility.cfw.sh/` e a sua API de builds pública.
- `https://scooterhacking.org/` (categorias públicas de funcionalidades).
- `https://github.com/scooterhacking` (a organização não é sinónimo do código
  completo do cliente atual; existem projetos públicos com licenças diferentes).
- Artefactos locais acima identificados e código MyNaviScooter.

Não foi estabelecida proveniência verificável do bootstrap histórico até ao
artefacto atual. Não foram publicados APKs, binários, firmware ou código extraído.
Esta é uma investigação estática com cobertura indicada, não uma alegação de
extração integral de duas versões ou certificação de segurança.
