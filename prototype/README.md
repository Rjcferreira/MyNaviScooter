# MyNaviScooter — protótipo visual

Abrir `index.html` diretamente no navegador.

## Interações incluídas

- Clicar no painel Bluetooth simula ligar/desligar a ligação.
- Ao desligar, o protótipo passa automaticamente para 22 km/h e desativa o Sport temporário.
- Clicar em “Sport temporário” alterna entre Sport temporário e Sport original.
- “Voltar ao original” restaura o perfil seguro.
- Eco e Drive selecionam os perfis originais.
- Dashboard, Navegação, Backup e Avançado funcionam como separadores.
- Navegação inclui uma rota simulada para validação visual do conceito.
- Backup mostra checksum, estado dos perfis e restauro completo simulado.
- Avançado mostra o heartbeat e o fallback seguro.

Este é um protótipo visual/simulado. Ainda não comunica com uma trotinete real, não lê firmware e não altera parâmetros físicos.

## Primeira app Android real

O cliente Android de diagnóstico BLE está na pasta [`android`](../android/). Recolhe serviços e tenta a comunicação inicial PRE_COMM, sem alterar configurações. Não é um backup restaurável. Ver [revisão técnica](../docs/review-2026-09-25.md).

## Integração ScooterHacking

Os repositórios públicos da ScooterHacking serão usados apenas por adaptadores validados. O `NinebotCrypto` documenta suporte conhecido para versões BLE antigas e F-series, mas avisa que versões não listadas podem ter problemas; por isso ZT3/F3/GT3 e Xiaomi 5 precisam de uma matriz de compatibilidade própria antes de qualquer escrita.
