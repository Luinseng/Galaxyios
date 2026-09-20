# Installare Gearan su iPhone (IPA) — guida onesta

## Punto fondamentale: senza APK sul Watch, l'app iPhone non può fare nulla

Non è un limite di Gearan, è fisica del Bluetooth + policy Apple:

- L'iPhone fa da **centrale BLE**: cerca solo il Service UUID Gearan
  (`6E400001-…`). Se sul Watch non gira l'app Gearan (APK installata e
  schermata "Pair iPhone" aperta), **niente trasmette** → la lista resta vuota.
- Il Watch4 **non espone** ad iOS alcun servizio generico di "associami":
  niente Galaxy Wearable su iOS, niente pairing di sistema verso terze parti,
  niente ANCS senza periferica che negozia il servizio Apple.
- Quindi l'ordine è obbligato: **1) APK sul Watch → 2) app su iPhone →
  3) entrambe le app aperte → 4) pairing**. Qualsiasi app che promette di
  associare il Watch "da sola" senza nulla sul Watch non sta dicendo il vero.

E anche dopo il pairing non sarà "nativo" come un Apple Watch: niente
telefonate/SMS di sistema, niente Health di sistema, niente sblocco con orologio.
Funziona tutto ciò che passa per Gearan (sync, batteria, relay HTTPS, notifiche Gearan).

## Perché non ti allego un file .ipa

Un `.ipa` installabile esiste solo se **firmato per il tuo specifico iPhone**
con il tuo Apple ID / provisioning profile. Io qui non ho macOS/Xcode né la
tua firma: qualunque `.ipa` generico verrebbe rifiutato dal tuo iPhone.
Devi compilarlo una volta (serve un Mac, anche in prestito o cloud).

## Strada consigliata: installazione diretta via cavo (niente .ipa)

1. Su Mac con Xcode 15+: crea il progetto come in `docs/XCODE_SETUP.md`
   (importa `GearanCore`, aggiungi i 3 file di `Sources/GearanApp/`).
2. Collega l'iPhone via cavo → selezionalo come destinazione di Run.
3. Signing & Capabilities → Team → il tuo Apple ID (va bene anche gratuito).
4. Premi Run: Xcode compila, firma e installa Gearan sull'iPhone.
5. Su iPhone: Impostazioni → Generali → VPN e gestione dispositivo →
   autorizza il tuo account sviluppatore.
6. Apri Gearan sul Watch → Pair iPhone, poi su iPhone → Add Watch.

## Se vuoi proprio il file .ipa

1. In Xcode: Product → Archive (con destinazione "Any iOS Device").
2. Window → Organizer → Distribute App → **Development** (o Ad Hoc se hai
   account a pagamento con UDID registrati) → Export → ottieni `Gearan.ipa`.
3. Installalo con uno di questi:
   - Xcode → Window → Devices and Simulators → trascina l'`.ipa`;
   - Apple Configurator;
   - AltStore / Sideloadly (firma con Apple ID gratuito, validità **7 giorni**,
     poi reinstallare).
4. Con account Developer a pagamento (99 €/anno): validità 1 anno e opzione
   TestFlight per inviti senza cavo.

## Problemi tipici

- "Untrusted Developer": vedi passo 5 sopra.
- Lista vuota su Add Watch: il Watch NON sta trasmettendo → apri Gearan sul
  Watch e premi Pair iPhone (advertising dura 120 s, poi ripeti).
- "Signing failed": nessun team selezionato o Bundle ID già usato da altri →
  cambia Bundle ID in `com.tuonome.gearan`.
- Firma gratuita scaduta (7 giorni): riesporta/reinstalla, i dati di pairing
  nel Keychain restano e il reconnect è automatico.
