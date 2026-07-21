# PlaytimeRewards

Plugin Maven per **Paper 1.21.11** compilato con **Java 26**.

## Funzioni

- Tiene traccia del tempo realmente trascorso online.
- Ogni 30 minuti consegna l'oggetto impostato e il denaro configurato.
- `/setplaytimereward` salva una copia esatta dello stack nella mano principale, inclusi quantità, nome, lore, enchant e metadati supportati da Paper.
- `/playtime` mostra il tempo registrato dal plugin.
- Tempo totale e avanzamento verso la ricompensa vengono salvati in `playerdata.yml` e ripristinati dopo riavvii o spegnimenti.
- Se l'inventario è pieno, gli oggetti avanzati vengono lasciati a terra, salvo modifica del config.

## Requisiti server

- Paper 1.21.11
- JVM Java 26
- Vault
- Un plugin economy compatibile con Vault per la parte monetaria

Vault è una dipendenza obbligatoria: senza il relativo JAR Paper non caricherà il plugin.

## Compilazione

Installa JDK 26 e Maven, poi dalla cartella del progetto esegui:

```bash
mvn clean package
```

Il JAR verrà creato in:

```text
target/PlaytimeRewards-1.0.0.jar
```

## Installazione

1. Copia il JAR nella cartella `plugins` del server.
2. Installa Vault e un provider economy, per esempio quello fornito dal tuo plugin economico.
3. Avvia il server.
4. Modifica `plugins/PlaytimeRewards/config.yml` per impostare `reward.money`.
5. Tieni in mano lo stack desiderato e usa `/setplaytimereward`.

## Comandi e permessi

| Comando | Descrizione | Permesso |
|---|---|---|
| `/playtime` | Mostra il proprio playtime | `playtimerewards.playtime` |
| `/setplaytimereward` | Salva l'oggetto nella mano principale | `playtimerewards.admin` |

## Config principale

```yaml
reward-interval-minutes: 30

reward:
  money: 100.0
  item: null
  drop-overflow-items: true

autosave-seconds: 300
```

La voce `reward.item` viene gestita automaticamente dal comando e non dovrebbe essere modificata manualmente.

## Nota sul playtime

Il contatore parte dal momento in cui il plugin viene installato. Non importa automaticamente le statistiche vanilla accumulate prima dell'installazione.
