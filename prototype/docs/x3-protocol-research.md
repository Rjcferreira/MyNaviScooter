# Pesquisa avançada — Ninebot X3

Data da pesquisa: 2026-09-24

## Descoberta nova

Existe uma documentação independente de engenharia reversa do protocolo Segway-Ninebot, acompanhada por um cliente BLE Python de referência. A documentação não se limita à SHU: foi construída a partir da análise da aplicação oficial Android, da biblioteca nativa `libnbcrypto.so` e de pacotes de configuração do fabricante.

Fonte principal: https://nootnooot.codeberg.page/segway-ninebot-ble/

## Matriz X3 encontrada

| Modelo | Server ID | Hardware ID | Comandos | BLE protocol | Encryption |
|---|---:|---:|---:|---:|---:|
| Segway ZT3 Pro | 10256 (`0x2810`) | 256 (`0x100`) | 191 | 2 | 2 |
| Segway eKickScooter F3 | 10259 (`0x2813`) | 259 (`0x103`) | 192 | 2 | 2 |
| Segway SuperScooter GT3 | 10257 (`0x2811`) | 257 (`0x101`) | 192 | 2 | 2 |

Para a ZT3 e F3, a combinação é `Protocol 2 + Encryption2`. Não devemos tratar os dois modelos como um único perfil: têm pacotes de configuração separados e podem ter comandos/valores diferentes.

## Transporte BLE

- Serviço Ninebot customizado: `6e400001-*-006e-696e65626f74`.
- Cabeçalho BLE: `5A A5`.
- Origem Bluetooth: `0x3E`.
- O destino é o módulo interno (BLE, VCU, MCU, BMS, TFT, etc.).
- A aplicação deve negociar MTU e fragmentar frames maiores que `MTU - 3`.
- A versão de encriptação pode ser confirmada pelos dados de advertising do fabricante `0x4E42`/`0x4E43`.

## Sessão e encriptação

Para Encryption2, a referência documenta:

1. `PRE_COMM` para obter challenge/identificador.
2. `SET_PWD` para estabelecer a password de sessão.
3. `AUTH` para autenticar.
4. AES-128 com contador de sessão e autenticação CBC-MAC.

O contador e a sessão têm de ser mantidos por ligação. Reutilizar frames ou misturar sessões é proibido pelo desenho do protocolo.

## Módulos conhecidos

Na X3 existem módulos separados. A ZT3 documentada pela ScooterHacking usa BLE `0x04`, MCU `0x02`, VCU `0x16`, BMS `0x07`/`0x06` e TFT `0x23`. A camada da app deve obter os endereços a partir do perfil de modelo, nunca de constantes globais.

## Dados descobertos no projeto segMod

O projeto independente `segMod`, desenvolvido em F3/GT3 e destinado também a ZT3/G3, publica uma tabela prática de registos X3. Entre os registos identificados estão:

- versões VCU/MCU/BMS;
- estado de carregamento e bateria;
- velocidade, temperatura, erro e modo atual;
- limites de velocidade Sport/Race;
- Eco/Drive/Sport/Race;
- regeneração, cruise control, luzes, TCS e assistências;
- comandos de keepalive/heartbeat e reset.

Fonte: https://raw.githubusercontent.com/MacintoshKeyboardHacking/segMod/main/myBLE4/x3regs.h

Esta tabela é excelente para montar o primeiro leitor de diagnóstico, mas muitos campos têm comentários `unknown`, `rw` ou dependem de modelo. Por isso, a primeira implementação só deve ler e comparar respostas; qualquer escrita deve passar por uma allowlist por modelo e firmware.

## Firmware e recuperação

O `x3utils` confirma suporte a ZT3 Pro, F3 e F3 Pro por ST-LINK/SWD, com dump completo, flashing e recuperação. Também avisa explicitamente que uma interrupção, ficheiro errado ou ligação incorreta pode inutilizar o controlador.

Fonte: https://github.com/ztakis/x3utils

Limites publicados pelo projeto:

- F3/G3: não usar o modo SHU-compatible em VCU `1.6.3` ou superior.
- ZT3: não usar o modo SHU-compatible em VCU `1.5.9` ou superior.

Esses limites serão regras bloqueantes na MyNaviScooter.

## O que isto permite construir

### Fase 1 — segura e implementável

- scanner BLE;
- seleção automática ZT3/F3;
- leitura de manufacturer data;
- negociação Protocol 2 + Encryption2;
- autenticação de sessão;
- leitura de versões e estado;
- leitor de telemetria;
- backup lógico de parâmetros lidos;
- matriz de compatibilidade e bloqueio de escrita insegura.

### Fase 2 — configuração

- leitura dos perfis Eco/Drive/Sport;
- confirmação de que Eco/Drive continuam originais;
- edição controlada do Sport;
- restauro do Sport original;
- heartbeat de sessão;
- restauração segura quando a sessão expira, se o firmware em uso suportar essa regra.

### Fase 3 — firmware

- apenas depois de um dump verificável;
- apenas para pacote cujo modelo, hardware e versão coincidam;
- sem aceitar ficheiros de outro modelo;
- com recuperação documentada e confirmação dupla.

## Conclusão

Já não estamos limitados à documentação resumida da ScooterHacking. Temos uma base pública suficiente para implementar a camada BLE e de leitura para ZT3/F3 com muito mais confiança. Ainda assim, “funcionar perfeitamente” exige validar cada combinação real de firmware com uma scooter física, porque a documentação independente é mais completa no transporte e nos registos do que na garantia de cada operação de escrita.
