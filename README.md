# MyNaviScooter

Projeto open source para diagnóstico BLE e gestão segura de perfis em scooters Segway/Ninebot.

## Estado atual

O projeto contém uma aplicação Android experimental de diagnóstico BLE. A revisão 0.2 recolhe serviços GATT e tenta PRE_COMM no Nordic UART observado numa captura oficial da ZT3. A autenticação completa e compatibilidade física com F3/GT3 ainda não estão validadas. O JSON não é um backup restaurável.

O código Android está em [`android/`](android/) e o protótipo visual em [`prototype/`](prototype/).

## Segurança

Esta versão escreve apenas a subscrição CCCD e o pedido de diagnóstico PRE_COMM; não altera configurações, credenciais, firmware ou velocidade. Backup autenticado, restauração e perfis temporários ainda não estão implementados. Ver [revisão técnica](docs/review-2026-09-25.md).

## Pesquisa e atribuições

O desenvolvimento é informado por ScooterHacking, NootNooot/segway-ninebot-ble, segMod e x3utils. Os seus respetivos códigos e licenças devem ser consultados antes de incorporar qualquer implementação. O repositório está sob AGPL-3.0 para manter o projeto aberto e permitir melhorias públicas.

## Compilação

Abrir `android/` no Android Studio. O workflow do GitHub Actions também prepara uma compilação de debug e publica o APK como artefacto quando há alterações no código Android.
