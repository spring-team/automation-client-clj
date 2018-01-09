#!/usr/bin/env bash

set -eu

git config --global user.email "travis-ci@atomist.com"
git config --global user.name "Travis CI"

die(){
  echo $1
  exit 1
}

lein do clean, test, jar

echo "TRAVIS_BRANCH: ${TRAVIS_BRANCH}"
echo "TRAVIS_PULL_REQUEST: ${TRAVIS_PULL_REQUEST}"
echo "ARTIFACTORY_USER:" ${ARTIFACTORY_USER}

if [ "${TRAVIS_BRANCH}" == "master" ] && [ "${TRAVIS_PULL_REQUEST}" == "false" ]; then
    lein release
fi
