Bot username: @carFantasy_bot

🚗 CarApiService

CarApiService è un servizio Java che interroga le API di Wikipedia per recuperare informazioni su automobili, includendo:

informazioni generali (descrizione)

schede tecniche dettagliate (infobox, se riesce ad estrapolare i dati da esse)

immagini del modello

validazione intelligente per escludere risultati non automobilistici

È pensato per essere usato, ad esempio, in bot Telegram, backend REST o applicazioni Java che forniscono dati automobilistici.

Funzionalità principali:
 -Ricerca intelligente su Wikipedia

 -Cerca prima su Wikipedia italiana, poi inglese

 -Filtra i risultati per garantire che siano realmente legati al mondo automotive (filtro personalizzabile sul codice)

Informazioni generali (/cerca):

 -Descrizione del veicolo o della marca

 -Testo estratto dal summary Wikipedia

 -Immagine del modello (se disponibile)

Scheda tecnica dettagliata (/dettagli):

 -Estrazione dei dati dall’infobox Wikipedia

 -Supporto ai template:

{{Auto-caratteristiche}}

{{Auto}} (fallback)

Logica di validazione automobilistica

Il servizio verifica che il risultato sia davvero un’auto tramite:

Categorie Wikipedia

matching su keyword automotive (auto, car, SUV, supercar, marchi, ecc.)

Esclusione semantica

persone, film, musica, sport, politica

Analisi del titolo

marchi automobilistici

termini tipici del settore (GT, Turbo, Sport…)

Questo riduce drasticamente i falsi positivi.

🧩 Metodi pubblici principali
Informazioni generali
SearchResult searchByMakeWithImage(String make)
String searchByMake(String make)

Scheda tecnica
SearchResult getModelDetailsWithImage(String model)
String getModelDetails(String model)


I metodi che restituiscono SearchResult includono opzionalmente l’immagine.

📦 Dipendenze

Il file utilizza:

OkHttp

implementation "com.squareup.okhttp3:okhttp"


Gson

implementation "com.google.code.gson:gson"

⚙️ Configurazione

È richiesto un User-Agent personalizzato per le API Wikipedia:

Config.get("WIKIPEDIA_USER_AGENT", "CarFantasyBot/1.0"); (Settato nel file CarApiService.Java)
Un file config.properties con il token del bot di telegram

Le immagini vengono estratte da:

thumbnail.source

originalimage.source

tramite le API REST di Wikipedia.

🧹 Pulizia del Wikitext

Il parser:

rimuove template, commenti, ref, link wiki

mantiene solo testo leggibile

tronca valori troppo lunghi

normalizza i nomi dei campi

🧪 Gestione errori

Messaggi chiari per:

risultati non automobilistici

dati non disponibili

errori di rete

Fallback automatici per garantire sempre una risposta utile

📌 Possibili casi d’uso

🤖 Bot Telegram / Discord

🌐 Backend REST per app automotive

📊 Servizi informativi su veicoli

🎮 Progetti di simulazione / fantasy car database