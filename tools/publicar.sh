#!/bin/sh
# Publica la estable (022): pone la etiqueta vX.Y.Z en el commit de la última beta de esa versión y la sube. Al
# llegar la etiqueta, .github/workflows/estable.yml publica ese mismo APK como release normal (la «latest»).
#
#   sh tools/publicar.sh estable
#
# Se niega si la versión ya tiene estable, si no hay beta publicada de esa versión o si en main hay cambios de la app
# posteriores a la beta (su beta aún no ha salido: hay que esperarla). Los cambios que no son de la app (docs, .md)
# no cuentan. Las rutas son las mismas que dispara .github/workflows/beta.yml: si cambian, cambiar las dos.
set -eu

APP_PATHS="app benchmark buildSrc gradle *.kts gradle.properties version.properties firma.sha256 .github/workflows/beta.yml"

die() { echo "publicar: $*" >&2; exit 1; }

[ "${1:-}" = "estable" ] || die "uso: sh tools/publicar.sh estable"

if command -v gh >/dev/null 2>&1; then GH=gh
elif [ -x "/c/Program Files/GitHub CLI/gh.exe" ]; then GH="/c/Program Files/GitHub CLI/gh.exe"
else die "no encuentro GitHub CLI (gh)"
fi

cd "$(git rev-parse --show-toplevel)"
git fetch -q origin main --tags

name=$(git show origin/main:version.properties | sed -n 's/^versionName=//p' | tr -d '\r[:space:]')
echo "$name" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' || die "version.properties de main: versionName no es X.Y.Z («$name»)"
tag="v$name"

if git rev-parse -q --verify "refs/tags/$tag" >/dev/null || [ -n "$(git ls-remote --tags origin "refs/tags/$tag")" ]; then
  die "la $name ya tiene estable ($tag). Para otra, sube versionName en version.properties y espera su beta."
fi

# La beta más reciente de esta versión: la de mayor versionCode.
beta=""
best=0
for t in $(git tag -l "v$name-beta.*"); do
  code=${t##*-beta.}
  case "$code" in ''|*[!0-9]*) continue ;; esac
  if [ "$code" -gt "$best" ]; then best=$code; beta=$t; fi
done
[ -n "$beta" ] || die "no hay ninguna beta de la $name (etiquetas v$name-beta.*). Sale sola al llegar a main un cambio de la app."

commit=$(git rev-list -n 1 "$beta")
git merge-base --is-ancestor "$commit" origin/main || die "la beta $beta no está en main"

# shellcheck disable=SC2086
if ! git diff --quiet "$commit" origin/main -- $APP_PATHS; then
  die "main tiene cambios de la app posteriores a $beta: espera a que salga su beta (pestaña Actions) y repite."
fi

state=$("$GH" release view "$beta" --json isPrerelease,assets --jq '(.isPrerelease|tostring) + " " + ([.assets[] | select(.name | endswith(".apk"))] | length | tostring)') \
  || die "la beta $beta no tiene release publicada"
[ "$state" = "true 1" ] || die "la release $beta no es una beta con un APK ($state)"

echo "Estable $tag = beta $beta (commit $(git rev-parse --short "$commit"))"
git tag -a "$tag" "$commit" -m "Tiempo $name"
git push -q origin "refs/tags/$tag"
echo "Etiqueta $tag subida. La estable la publica GitHub en un par de minutos: pestaña Actions › estable."
