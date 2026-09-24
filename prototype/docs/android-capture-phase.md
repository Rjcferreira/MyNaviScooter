# Fase Android: captura segura da unidade real

O projeto `mynavi-scooter-android` é a ponte entre a pesquisa pública e a unidade ZT3 do utilizador.

## Contrato de segurança

Nesta versão, o código só chama `discoverServices()` e `readCharacteristic()` para características com `PROPERTY_READ`. Não existem chamadas de escrita, flash, IAP ou alteração de perfil.

## O que precisamos do primeiro ficheiro

O JSON exportado vai permitir comparar:

- nome e endereço BLE anunciado;
- dados de fabricante e assinatura X3;
- serviços e características efetivamente expostos pela ZT3;
- quais leituras são públicas antes de autenticação;
- firmware/identificadores que eventualmente apareçam como valores legíveis.

A partir daí, o próximo passo é adicionar o protocolo autenticado documentado para o par `serverId=10256 / hardwareId=256`, mas apenas com comandos de leitura. O backup completo e qualquer configuração ficam bloqueados até haver uma matriz de compatibilidade para a versão exata da scooter.
