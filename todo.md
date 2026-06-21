# TODO

Tylko otwarte rzeczy. Historia zrobionego = git log.

## Następny milestone — jeden feature z 2 poziomów: application + infrastructure HTTP

Dowód tezy z Readme „same behaviour, different entry point". Dziś feature'y chodzą tylko
z warstwy application. Cel: ten SAM `register.feature` napędzany też z infry (wejście HTTP).
NIE kopiujemy Gherkina — jeden plik, dwa zestawy step-defs.

Czemu `register.feature` pierwszy: brak zegara/brute-force/sesji (czysta walidacja + zapis).

Kroki:
1. Odkomentować `security-infrastructure` w parent pom; doprowadzić do BUILD SUCCESS
   (prod DI: `BeanFactory` + `AuthenticationFactory` + `RandomBlockDurationPolicy`).
2. `SecurityController` realnie: `POST /register` → `SecurityService.register` → HTTP
   (Registered → 2xx, Rejected → 4xx z `emailErrors`/`passwordErrors().codes()`).
3. Drugi zestaw step-defs dla tego samego feature, uderzający po HTTP (MockMvc/WebTestClient).
4. Runner: ten sam `.feature`, inne glue (osobny pakiet/profil Cucumbera).
5. UI (3. poziom) — później (front/driver, mały zysk ponad app+infra).

Otwarta poddecyzja: jak współdzielić `.feature` między runnerami (jeden plik + dwa
`@SelectClasspathResource` z różnym glue-base, vs jeden runner z dwoma profilami).
⚠️ Nieweryfikowalne, dopóki `security-infrastructure` poza reaktorem.

## Hardening rejestracji (większy temat)

`register(email, password)` nie ma IP/throttlingu. Luki: DoS przez haszowanie, masowe
fałszywe konta, enumeracja kont. Osobna kontrola od guarda uwierzytelniania. Do decyzji: czy domykać.

## `Source` jako podmiot domeny (większy design)

Guard/`AuthenticationBlock` biorą `Source`, `IpAddress` staje się jego polem. Rozdzielić role:
- TOŻSAMOŚĆ (klucz bloku, `equals/hashCode`): tylko trudne do podmiany (IP/podsieć/ASN/konto).
- OBSERWOWANE (machineName, browserVersion): forensics, POZA `equals` — inaczej zmiana
  user-agenta = obejście lockoutu. Rozważyć `DeviceFingerprint`/`RequestContext`. Uwaga RODO.

## Zaparkowane

- **Glosariusz / cross-linking UL** — termin domenowy jako hiperłącze w Gherkin/Allure/Javadoc
  (Python static-site generator parsujący `.feature` + źródła + allure-results). Za grube na teraz.
