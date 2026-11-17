# webOS IPTV Player dla Minix NEO-U1 (v8)

## Nowości i naprawy w wersji 8.0
- **Eliminacja zawieszania się (Crash):** Dodano kompleksowy system globalnego przechwytywania wyjątków JavaScript oraz logowania błędów (`window.appendAppLog`).
- **Kafelek i okno „Logi i Diagnostyka” w ustawieniacz:** Przycisk w menu Ustawień otwierający podgląd błędów dekodowania, wykrzykników ExoPlayera, statusów HTTP oraz żądań sieciowych z możliwością skopiowania do schowka.
- **Błyskawiczny start strumienia (Bufor startowy skrócony do 1.5s):** Wyeliminowano 20-sekundowe mrożenie pierwszej klatki po przełączeniu kanału na Amlogic S905.
- **Pełne wsparcie dla dekodera FFmpeg (AC3 / MP2 / MPG)**, ulubionych pod klawiszem **`F`** oraz EPG z listy IPTV.
