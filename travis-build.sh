#!/usr/bin/env bash

set -eu

git config --global user.email "travis-ci@atomist.com"
git config --global user.name "Travis CI"

export PROJECT_VERSION=`lein project-version`

die(){
  echo $1
  exit 1
}


echo "TRAVIS_BRANCH: ${TRAVIS_BRANCH}"
echo "TRAVIS_PULL_REQUEST: ${TRAVIS_PULL_REQUEST}"
echo "PROJECT_VERSION: ${PROJECT_VERSION}"

SEMVER=`echo $PROJECT_VERSION | cut -d'-' -f1`
SNAPSHOT=`echo $PROJECT_VERSION | cut -d'-' -f2`

unset CLASSPATH
if [ "${TRAVIS_BRANCH}" == "master" ] && [ "${TRAVIS_PULL_REQUEST}" == "false" ] && [[ $TRAVIS_TAG =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    if [ "${TRAVIS_TAG}" == "${SEMVER}" ]; then
        echo "Performing release: $SEMVER"
        lein set-version $TRAVIS_TAG || die "Error updating version"
        lein deploy clojars
    else
        die "Semver tag does not match project version (with or without -SNAPSHOT)"
    fi
elif [ "$SNAPSHOT" == "SNAPSHOT" ]; then
    echo "Doing SNAPSHOT build..."
    lein do clean, test, jar
    lein deploy clojars

else
    die "Project version is not a snapshot, and no matching semver tag found!"
fi
