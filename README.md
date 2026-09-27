# MyNaviScooter

Projeto open source para diagnóstico BLE e gestão segura de perfis em scooters Segway/Ninebot.

## Estado atual

O projeto contém uma aplicação Android experimental de diagnóstico BLE para a família X3. A revisão 0.2 implementa descoberta GATT, PRE_COMM, emparelhamento autorizado pelo botão, autenticação e um backup cifrado dos parâmetros necessários antes de qualquer alteração. O scanner profundo opcional inventaria, em modo só de leitura, os registos documentados da VCU, MCU, BLE e BMS. Compatibilidade física com F3/GT3 ainda não está validada.

O código Android está em [`android/`](android/) e o protótipo visual em [`prototype/`](prototype/).

## Segurança

O scanner profundo não faz uma varredura cega da memória: limita-se a comandos de leitura e offsets declarados no perfil público da ZT3. Um novo emparelhamento pode trocar a credencial Bluetooth; as operações de perfil, quando explicitamente escolhidas, permanecem separadas do scanner. Não são enviados comandos de firmware. Ver [revisão técnica](docs/review-2026-09-25.md).

## Pesquisa e atribuições

O desenvolvimento é informado por ScooterHacking, NootNooot/segway-ninebot-ble, segMod e x3utils. Os seus respetivos códigos e licenças devem ser consultados antes de incorporar qualquer implementação. O repositório está sob AGPL-3.0 para manter o projeto aberto e permitir melhorias públicas.

## Compilação

Abrir `android/` no Android Studio. O workflow do GitHub Actions também prepara uma compilação de debug e publica o APK como artefacto quando há alterações no código Android.
