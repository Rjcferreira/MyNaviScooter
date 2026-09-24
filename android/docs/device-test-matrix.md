# Matriz das unidades de teste

## ZT3 Pro — primeira unidade

- Estado: original de utilização.
- Alteração conhecida: Zero Start ativado através do ScooterHacking Utility.
- Firmware observado na app Segway:
  - Vehicle Controller / VCU: `1.5.9`
  - Battery / BMS: `4.1.6.8`
  - Bluetooth / BLE: `2.1.13`
  - Motor Control / MCU: `1.6.0`
- Tratamento: capturar e guardar como `baseline_current_user_state`.
- Não assumir que esta captura representa os valores de fábrica.
- Não executar escritas ou flash durante a primeira sessão. Em particular, o VCU `1.5.9` fica bloqueado para qualquer caminho de flash SHU-compatible até confirmação específica.

## F3 Pro — segunda unidade

- Estado: modificada integralmente através do ScooterHacking Utility.
- Tratamento: usar mais tarde como comparação entre firmware/configuração original e modificada.
- Não usar como alvo inicial de desenvolvimento, porque uma modificação completa pode esconder diferenças de firmware e de parâmetros.

## Ordem de validação

1. ZT3: fingerprint BLE, serviços, versões e captura somente de leitura.
2. ZT3: confirmar se os valores observados correspondem ao Zero Start conhecido.
3. ZT3: criar o primeiro backup autenticado, sem modificar a scooter.
4. F3 Pro: repetir a captura e comparar os módulos/versões.
5. Só depois avaliar perfis temporários e mecanismos de fallback.

O primeiro ficheiro exportado da ZT3 deve ser preservado como referência. Não o substituir depois de fazer alterações.
