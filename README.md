# webOS IPTV Player dla Minix NEO-U1 (v7)

## Najważniejsze zmiany w wersji 7.0
- **Automatyczna obsługa BehindLiveWindowException:** Wyeliminowano zatrzymywanie odtwarzacza przy chwilowych przycięciach lub przesunięciach strumienia na żywo — odtwarzacz samoczynnie doskakuje do aktualnej krawędzi transmisji (Live Edge).
- **Poprawiony kod w MainActivity.java (`sanitizeForJs`):** Usunięto błędy składniowe w eskejpowaniu znaków dla JavaScript.
- **Dekoder FFmpeg dla AC3 / MP2 / MPG:** Płynne odtwarzanie dźwięku na wszystkich kanałach z wielokanałowym lub nietypowym kodowaniem.
- **Funkcja Ulubione kanały (`★ Ulubione`)** z obsługą klawisza **`F`**.
- **Automatyczny fallback na EPG z listy IPTV** po wyłączeniu zewnętrznego przewodnik.
