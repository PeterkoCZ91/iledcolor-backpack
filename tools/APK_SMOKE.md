# Test APK přes ADB

Python 3 (pouze standardní knihovna) a `adb` v PATH. Telefon musí být odemčený a autorizovaný pro USB ladění. Oprávnění aplikace k Bluetooth potvrď před testem; skript systémové dialogy automaticky nepotvrzuje. Během běhu s telefonem nepracuj.

Spouštěj z kořene repozitáře:

```sh
python3 tools/apk_smoke.py
python3 tools/apk_smoke.py --apk ../BatohManager-v45-debug.apk
python3 tools/apk_smoke.py --serial DEVICE_SERIAL
```

Výsledky: `artifacts/apk-smoke/<čas>.json` v projektu (gitignored). Exit 0 = prošly automatické kontroly, exit 1 = selhání. Každý krok vychází z čerstvého UIAutomator XML, kontroluje balíček a hledá konkrétní text/popisek; souřadnice odvozuje z nalezeného ovládacího prvku. Čekání mají limit.

Skript zastaví proces aplikace a otevře novou sešnu, aby začal bez aktivního připojení k batohu. Spouštěj až po dokončení probíhajících downloadů/uploadů. Neodinstaluje aplikaci, nemaže její data, nemění GIFy ani nezapisuje obsah do batohu. `--apk` výslovně povolí aktualizační instalaci s ponecháním dat. Kontroluje Home, verzi proti skutečným metadatům APK, nastavení, knihovnu, ovládání batohu, sbalenou diagnostiku a otevření/zrušení výběru z vlastní knihovny. Čte crash buffer od začátku vlastního běhu; staré pády před testem nevyhodnocuje. Neukládá surové logy, XML ani sériové číslo.

Volitelná kontrola se skutečným batohem:

```sh
python3 tools/apk_smoke.py --hardware
```

Tato volba se připojí k batohu, ověří, že synchronizace času a dotaz na stav neskončily chybou a že UI zobrazí jas i stav displeje, pak otevře dialog „Smazat obsah batohu“ a stiskne **Zrušit**. Smazání nikdy nepotvrzuje, skutečný upload neprovádí. Bez `--hardware` je tato kontrola označená jako skipped.

Anglický průchod obrazovkami:

```sh
python3 tools/apk_smoke.py --fixtures --locale-sweep
```

Přečte vybraný jazyk v nastavení, projde hlavní obrazovky v angličtině, vrátí původní volbu a ověří také české kategorie, hledání, sbírku a batoh. Platný fixture GIF se otevře v detailu i editoru v obou jazycích. Cleanup odstraní pouze přesná URI vložená tímto během. Nevyžaduje připojený batoh.

## Ruční kontrola

1. Otevři knihovnu s existujícím GIFem, ověř animovaný náhled i detail. Poškozený GIF má ukázat chybu bez pádu aplikace.
2. Najdi GIF, stáhni ho do knihovny a odešli šipkou. Sleduj název/náhled, přípravu, připojování, procenta a trvalé „Hotovo“; ověř obraz na batohu.
3. Během přenosu odejdi z obrazovky a vrať se: přenos i stav mají pokračovat.
4. Při dalším uploadu dej „Zrušit nahrávání“, pak „Zkusit znovu“. Ověř, že se starý přenos nepřekrývá s novým a retry znovu připojí batoh.
5. Vypni batoh během přenosu: zobrazí se chyba, ne falešné Hotovo. Znovu zapni a opakuj.
6. Ověř neznámý stav displeje, načtení stavu, jas a displej. Smazání obsahu zkoušej jen výslovně, až máš GIFy uložené v telefonu.
7. V Nastavení aplikace přepni jazyk na **English** a projdi domovskou obrazovku, kategorie, hledání, knihovnu, editor a batoh. Aplikaci zavři a znovu otevři; volba má zůstat uložená. Pak vyber **Podle systému**. Na Androidu 13+ ověř také jazyk v systémových informacích o aplikaci.

Vizuální kvalita a přehrávání na batohu nejsou potvrzené pouhým otevřením UI. Výchozí běh nevytváří testovací GIFy ani nemaže žádné existující soubory.

## Volitelné GIF regresní testy

```sh
python3 tools/apk_smoke.py --fixtures
```

Pouze tato explicitní volba vytvoří dva testovací MediaStore záznamy v `Pictures/GifPack` pod unikátním názvem `BatohSmoke_<náhodné ID>_valid.gif` a `..._malformed.gif`. Potřebuje Android 10+ a předem udělený plný přístup aplikace k obrázkům; vybrané fotografie samy nestačí. Oprávnění skript sám nemění.

Platný GIF má 64×64 a dvě barevné animované fáze. Poškozený má GIF hlavičku, descriptor a neplatný/truncated LZW blok bez platné první fáze. Test otevře oba z knihovny: pro platný čeká na skutečné `onSuccess` dekódování („Náhled GIFu načten“), pro poškozený vyžaduje srozumitelnou chybu, návrat do knihovny a žádný fatal crash. Pokud decoder poškozený soubor překvapivě přijme, test selže místo nepravdivého tvrzení o otestování chybové větve. GIFy se neposílají na batoh.

Blok `finally` odstraní **pouze přesné URI záznamů vložených tímto během**, i při chybě testu. Ostatní GIFy se nemažou. Přerušení procesu kill -9 nebo odpojení telefonu může cleanup znemožnit; JSON report označí selhání cleanupu. Výchozí běh bez `--fixtures` zůstává beze změn GIFů.

## Výslovné odeslání testovacího GIFu do batohu

```sh
python3 tools/apk_smoke.py --fixtures --upload
```

`--upload` funguje pouze společně s `--fixtures` a skutečně zapíše platný červený/modrý GIF do zapnutého batohu. Skript vyhledá šipku na kartě vlastního platného GIFu v knihovně, čeká na potvrzený úspěch přenosu a ověří, že výsledek zůstane viditelný i po opuštění a novém otevření ovládání. Poškozený soubor se nikdy neposílá. Test nezadává příkaz pro smazání batohu; testovací animace proto v batohu může zůstat. Cleanup odstraní pouze oba vlastní testovací GIFy z telefonu.

Fyzické přehrávání správných barev ověř na batohu očima. ADB test dokládá UI a dokončení protokolového uploadu, nikoli obraz na skutečném panelu. `--hardware` lze kombinovat s uploadem, ale pouze navíc otevře a zruší dialog smazání.

## Zrušení a opakování uploadu

```sh
python3 tools/apk_smoke.py --fixtures --cancel-upload
```

Tato varianta přidá vlastní platný 64×64 GIF s 96 snímky, zahájí jeho skutečné nahrávání, stiskne „Zrušit nahrávání“ a poté „Zkusit znovu“. Ověří stav zrušení bez falešného úspěchu a úspěšný stav po opakování i po navigaci. Retry testovací animaci do batohu zapíše; potvrzení smazání skript nikdy nestiskne. Po telefonu uklidí pouze vlastní MediaStore GIFy.

## Import z galerie / sdílení

```sh
python3 tools/apk_smoke.py --fixtures --import
```

`--import` vyžaduje `--fixtures`. Přes skutečný `ACTION_SEND` s grantem URI testuje studený start aplikace s platným GIFem a následné sdílení poškozeného GIFu do běžící aplikace. Platný soubor musí vytvořit právě jednu vlastní kopii a otevřít detail s načteným náhledem a tlačítky odeslat/upravit; poškozený musí zobrazit chybu a nevytvořit kopii. Přidané kopie skript dohledává pouze podle unikátního prefixu tohoto běhu a ukládá jejich přesná URI pro cleanup. Žádné jiné GIFy nesmaže. Kombinovat lze `--fixtures --import --upload`.

Import přes tlačítko „Importovat GIF“ s Android pickerem ověř i ručně: vyber GIF mimo složku GifPack, odeber aplikaci široký přístup k fotografiím (Android 10+) a ověř, že díky picker grantu vznikne vlastní kopie. Zrušení pickeru nevytvoří žádný soubor.
