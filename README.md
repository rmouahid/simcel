# SimCel — Simulation de Propagation de Feu

Automate cellulaire 2D simulant la propagation d'un incendie dans un environnement naturel, avec interface graphique JavaFX et mode console headless.

Projet réalisé dans le cadre du cours de Génie Logiciel — cursus ingénieur informatique (CY Tech).

---

## Fonctionnalités

### Simulation

- Grille 2D paramétrable avec 6 états de cellule : `VIDE`, `SAIN`, `EN_FEU`, `BRÛLÉ`, `EAU`, `ROCHER`
- 5 types de terrain avec inflammabilité et durée de combustion propres (forêt, prairie, broussailles, zone humide, zone urbaine)
- Paramètres environnementaux : vent (8 directions, intensité 0–5) et humidité (0–100 %)
- Propagation stochastique avec double-buffering (décisions basées sur un snapshot figé)
- Historique des 100 derniers états pour reculer tick par tick (`step back`)

### Contrôle

- Démarrer / Pause / Step avant / Step arrière / Stop / Reset
- Vitesse de simulation ajustable (délai entre ticks)
- Sauvegarde et chargement d'une simulation (format binaire `.simcel`)

### Interface graphique (JavaFX)

- Rendu de la grille sur Canvas avec zoom (×0.2 à ×8, centré sur le curseur) et panoramique (clic-glisser)
- 9 outils d'édition : modifier le type de terrain ou l'état de n'importe quelle cellule à la souris
- Statistiques en temps réel : tick, compteurs SAIN / EN_FEU / BRÛLÉ, taux de destruction
- Courbe d'évolution dynamique (3 séries, fenêtre glissante de 300 points)
- Indicateur de vent : boussole animée, force et humidité
- Légende des types de terrain

### Mode console (headless)

- Rendu ANSI dans le terminal
- Commandes interactives : `wind`, `humidity`, `speed`, `help`

---

## Prérequis

- Java 21
- Maven 3.8+
- JavaFX 21 (géré automatiquement via Maven)

---

## Installation

```bash
git clone https://github.com/rmouahid/simcel.git
cd simcel
```

---

## Lancement

### Mode GUI

```bash
mvn javafx:run
# ou
bash launchers/run-gui.sh
```

Paramètres par défaut : grille 50×30, 60 % forêt / 20 % prairie / 10 % broussailles / 5 % zone humide / 5 % zone urbaine, feu déclenché au centre, délai 200 ms.

### Mode console (headless)

```bash
bash launchers/run-cli.sh [options]
# ou
mvn exec:java -Dexec.args="--headless [options]"
```

| Argument           | Description                                                          | Défaut |
| ------------------ | -------------------------------------------------------------------- | ------ |
| `--width`          | Largeur de la grille                                                 | 40     |
| `--height`         | Hauteur de la grille                                                 | 20     |
| `--ticks`          | Nombre maximum de ticks                                              | 100    |
| `--delay`          | Délai entre chaque tick (ms)                                         | 200    |
| `--wind-direction` | Direction du vent : `N` `NE` `E` `SE` `S` `SO` `O` `NO`              | `N`    |
| `--wind-strength`  | Force du vent dans `[0, 5]` (0 = calme, 5 = tempête)                 | `0`    |
| `--humidity`       | Humidité ambiante dans `[0, 100]` (0 = sec, 100 = propagation nulle) | `0`    |

Exemples :

```bash
# Vent fort vers le Nord, terrain sec — propagation rapide
bash launchers/run-cli.sh --wind-direction N --wind-strength 5 --humidity 10

# Vent diagonal NE modéré, humidité élevée — propagation ralentie
bash launchers/run-cli.sh --wind-direction NE --wind-strength 3 --humidity 80

# Sans vent, humidité maximale — propagation nulle
bash launchers/run-cli.sh --wind-strength 0 --humidity 100
```

---

## Modèle de propagation

La probabilité d'inflammation d'une cellule voisine à chaque tick est :

```
P = clamp(baseInflammabilité × facteurVent × facteurHumidité, [0, 1])

facteurVent     = 1 + (force / 5) × cos(θ)
facteurHumidité = 1 − humidité / 100
```

`θ` est l'angle entre la direction du vent et le vecteur source → cible.  
Avec une force de 5, la probabilité est **doublée** dans le sens du vent et **nulle** en sens opposé.  
À 100 % d'humidité, toute propagation est impossible quelle que soit la force du vent.

---

## Architecture

L'application suit le patron **MVC** et le patron **Observer** (SimulationListener) :

| Couche     | Package      | Classes principales                                                        |
| ---------- | ------------ | -------------------------------------------------------------------------- |
| Modèle     | `model`      | `Cell`, `Grid`, `Environment`, `FireSimulator`, `SimulationIO`             |
| Vue        | `view`       | `GridView`, `ConsoleView`, `StatisticsPanel`, `ChartView`, `WindIndicator` |
| Contrôleur | `controller` | `SimulationController`                                                     |

### Flux de données

```
FireSimulator.tick()
  → snapshot figé (double-buffering)
  → calcul des ignitions et extinctions
  → notifyListeners()
      ├→ GridView        (redessine le Canvas)
      ├→ StatisticsPanel (met à jour les compteurs)
      └→ ChartView       (ajoute un point à la courbe)
```

### Sauvegarde / Chargement

`SimulationIO` sérialise un `SimulationSnapshot` (état courant, état initial, environnement, tick) dans un fichier `.simcel`. Le chargement vérifie que les dimensions correspondent avant de restaurer.

---

## Build

```bash
mvn compile          # Compilation
mvn package          # Génération du JAR (target/)
mvn javadoc:javadoc  # Javadoc → target/site/apidocs/
```

---

## Diagrammes UML

Disponibles dans [`docs/uml/`](docs/uml/) (sources PlantUML dans [`docs/uml/puml/`](docs/uml/puml/)).

---

## Convention de branches

| Branche           | Usage                |
| ----------------- | -------------------- |
| `main`            | Code stable livrable |
| `dev` / `develop` | Intégration continue |

---

## Équipe

- [Rayân MOUAHID](https://github.com/rmouahid)
- [Sofiane HOUMMASS](https://github.com/sofianehms)
