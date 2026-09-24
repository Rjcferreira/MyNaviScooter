# MyNaviScooter

Projeto open source para diagnóstico BLE e gestão segura de perfis em scooters Segway/Ninebot.

## Estado atual

O projeto começa com uma aplicação Android de captura BLE somente de leitura para ZT3 Pro, F3/F3 Pro e GT3. A primeira captura da ZT3 recolhe identificação, serviços GATT e características legíveis sem escrever na scooter.

O código Android está em [`android/`](android/) e o protótipo visual em [`prototype/`](prototype/).

## Segurança

Esta versão não executa comandos de escrita, flash ou alteração de velocidade. O backup autenticado, restauração e perfis temporários só serão ativados depois de validar o modelo e as versões reais de cada unidade.

## Pesquisa e atribuições

O desenvolvimento é informado por ScooterHacking, NootNooot/segway-ninebot-ble, segMod e x3utils. Os seus respetivos códigos e licenças devem ser consultados antes de incorporar qualquer implementação. O repositório está sob AGPL-3.0 para manter o projeto aberto e permitir melhorias públicas.

## Compilação

Abrir `android/` no Android Studio. O workflow do GitHub Actions também prepara uma compilação de debug e publica o APK como artefacto quando há alterações no código Android.
