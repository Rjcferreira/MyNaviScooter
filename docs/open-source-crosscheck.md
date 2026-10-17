# Cruzamento de fontes abertas — 17-10-2026

## Identidade e licença

| Referência | Revisão consultada | Licença | Utilidade e limite |
| --- | --- | --- | --- |
| [Vexora](https://github.com/tezitec-png/Vexora) | `c8c1ea1043e69a9aac25c5e96bfcbd004a008b57` | MIT, copyright ignition | Sessão BLE, controlos, releitura, documentação; não mantida |
| [scootscoot](https://github.com/andyshinn/scootscoot) | `71bcbdee131f71538893dc280becad601672c597` | Apache-2.0 | Biblioteca de telemetria; perfil validado numa GT3 Pro, não numa ZT3 |
| [NinebotCrypto](https://github.com/scooterhacking/NinebotCrypto) | Página pública consultada | AGPL-3.0 | Implementação do protocolo; não é o código completo da Utility |
| [Documentação NootNooot](https://nootnooot.codeberg.page/segway-ninebot-ble/) | Documentação pública consultada | Verificar ficheiros antes de incorporar código | Referência independente já citada no projeto |

Não se incorporou código-fonte de terceiros nesta alteração. Os factos e as
diferenças encontrados orientaram validação adicional e uma implementação própria.
Se forem incorporadas partes de código em mudanças futuras, devem preservar-se
licenças, avisos e atribuições dos respetivos ficheiros, não apenas do repositório.
Não foram republicados os firmwares incluídos na Vexora: a licença do projeto
não demonstra por si só a origem ou os direitos sobre cada imagem de firmware.

## Resultados verificáveis

1. Vexora `public/assets/flasher.js`, `readRegister`: confere o endereço de
   registo e o comprimento recebido. Uma resposta incompleta não é uma medição.
2. scootscoot `src/scootscoot/decode.py`: devolve ausência quando faltam bytes;
   as escalas são atributos do perfil, não verdades universais da família X3.
3. scootscoot `profiles/gt3_pro.py`: declara 20 células/40 bytes e odómetro U32
   a 0,1 km para o equipamento testado; isto difere das 13 células/26 bytes e da
   escala observada nos relatórios ZT3 do proprietário.
4. Vexora `live.js`: algumas operações de perfis também alteram flags de luzes
   e modo, não apenas velocidade. Não satisfazem automaticamente o requisito
   de deixar todas as outras configurações intactas.
5. Há conflito na interpretação de `0x48`: o percurso de perfis Vexora escreve
   uma palavra LE, enquanto o bootstrap SHU histórico e o nosso teste anterior
   usam máscaras de bytes. Pode refletir firmware custom, modelos ou versões
   diferentes. Não selecionar uma interpretação por conveniência.
6. O texto de ajuda da própria Vexora exclui desbloqueio BLE da ZT3 recente e
   alerta para comportamento de assistência após desligar em algumas unidades
   com firmware custom. Isto é um alerta do autor, não um ensaio nosso.
7. O teste `dash-smoke.js` da Vexora usa modo demo. Aprovar esses testes não
   prova comunicação física, eficácia do desbloqueio ou segurança do firmware.
8. As funções comunitárias Vexora dependem de backend. Não foram implantadas:
   o projeto mantém a decisão do proprietário de usar apenas GitHub por agora.

## Alterações próprias resultantes

- A página Perfis passa a mostrar configurações efetivamente capturadas: VCU,
  versões codificadas dos restantes módulos, arranque com/sem impulso,
  desligar automaticamente, limite de carga e campos de velocidade em bruto.
- Este painel é só de leitura; não cria comandos nem implica autorização
  para ativar ou desativar as configurações apresentadas.
- Leituras truncadas, hex inválido, nomes duplicados, placa/endereço errados e
  percentagens fora de 0–100 deixam de ser apresentadas como medições válidas.
- Preservada a escala ZT3 anteriormente observada. Não foi aplicada à ZT3 a
  escala/quantidade de células GT3 Pro sem confirmação física.
- Testes unitários próprios cobrem validação, campos ausentes, arranque,
  comprimento, duplicados e percentagens. Os testes Android existentes cobrem
  as páginas; o teste da baseline multi-scooter já passou na build anterior.

## O que não está provado

Não há demonstração nova de desbloqueio, alteração de região, firmware custom,
compatibilidade elétrica, funcionamento em todos os modelos ou segurança em
circulação. Nenhuma ligação à scooter foi feita nesta investigação. Testes locais
ou emulador são provas de comportamento do software, não certificação do veículo.
