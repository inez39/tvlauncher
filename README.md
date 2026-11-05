# Ecrã inicial para a TV TCL (Google TV)

Launcher feito à medida: botões de entrada (TV NOS, HDMI 2, HDMI 1, Jogos), relógio e tempo de Coimbra,
novidades Apple TV+, YouTube em destaque, Peppa Pig em português e agenda de hoje.

## Chaves (GitHub > Settings > Secrets and variables > Actions > New repository secret)

| Nome | Para que serve | Obrigatória |
|---|---|---|
| `TMDB_KEY` | Filmes e séries da Apple TV+ | Sim |
| `YT_KEY` | YouTube e Peppa Pig | Sim |
| `CAL_ICS_URL` | Agenda (endereço secreto em formato iCal do Google Calendar) | Não |
| `PEPPA_CHANNEL_ID` | Limitar a Peppa Pig ao canal oficial | Não |

O tempo (Open-Meteo) não precisa de chave.

## Obter o APK

Separador **Actions** > última execução > **Artifacts** > `tv-launcher-apk`.

## Limites conhecidos

- "Melhores do YouTube" = vídeos mais populares em Portugal.
- A agenda mostra repetições simples (diária, semanal, mensal, anual). Exceções a eventos repetidos não são tratadas.
- Ao carregar num filme da Apple TV+, abre-se a app Apple TV (não o filme exato).
