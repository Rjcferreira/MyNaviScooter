# MyNaviScooter Capture — primeira versão Android

Esta é a primeira fase real da app: captura BLE somente de leitura para a Segway ZT3 Pro, F3/F3 Pro e GT3.

## O que faz

- pede as permissões Bluetooth do Android;
- procura anúncios BLE com identificação Segway/Ninebot/X3;
- reconhece os perfis ZT3/F3/GT3 quando o nome está disponível;
- liga à scooter e descobre serviços GATT;
- lê apenas características que anunciam a propriedade `READ`;
- exporta um JSON de diagnóstico com serviços, características, valores legíveis, RSSI e fabricante;
- não executa comandos `WRITE`, não faz flash e não altera velocidade/perfis.

## Abrir e instalar

1. Abrir esta pasta no Android Studio recente.
2. Deixar o Android Studio instalar o Android SDK 35 e o Gradle wrapper se solicitado.
3. Executar em Android 8.0 ou superior com Bluetooth ligado.
4. Na scooter, manter a scooter ligada e perto do telefone.
5. Tocar em `Procurar scooters`, selecionar a ZT3 e aguardar a captura.
6. Tocar em `Exportar captura para análise` e enviar o JSON.

## Limites desta fase

O protocolo autenticado X3 pode esconder leituras até a sessão ser autenticada. Isso é intencional: primeiro recolhemos o fingerprint e o GATT real da unidade, sem arriscar uma escrita. O JSON exportado é um diagnóstico, não um backup de firmware completo.

Depois de analisarmos a captura real, implementamos o leitor autenticado específico da versão da ZT3 e só então o backup completo e a restauração.

## Unidades de teste atuais

A ZT3 deve ser a primeira unidade. Está original relativamente ao firmware/configuração, mas tem o Zero Start ativado pelo ScooterHacking, portanto a primeira captura representa o estado atual do utilizador e não necessariamente o estado de fábrica. A F3 Pro está completamente modificada e será usada mais tarde apenas para comparação. Ver [`docs/device-test-matrix.md`](docs/device-test-matrix.md).
