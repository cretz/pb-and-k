#!/usr/bin/env groovy

@Library('toast@master')

import toastBuild

def discardStrategy
if (env.JOB_BASE_NAME.contains('PR')) {
    discardStrategy = logRotator(
        artifactDaysToKeepStr: '5',
        artifactNumToKeepStr: '',
        daysToKeepStr: '1',
        numToKeepStr: ''
    )
} else {
    discardStrategy = logRotator(
        artifactDaysToKeepStr: '25',
        artifactNumToKeepStr: '',
        daysToKeepStr: '100',
        numToKeepStr: ''
    )
}

properties([
    buildDiscarder(discardStrategy),
    pipelineTriggers([
        githubPush()
    ]),
    [
        $class   : 'ScannerJobProperty',
        doNotScan: false
    ],
])

toastBuild {
    stage('pre-build') {
        checkout scm
    }
    stage('build') {
        sh(
            encoding: 'UTF-8',
            returnStatus: false,
            returnStdout: false,
            script: "./gradlew build --stacktrace --no-daemon"
        )
    }
    stage('deploy') {
        if (env.JOB_BASE_NAME.contains('PR')) {
            println 'Deployments disabled for pull request builds.'
        } else {
            sh(
                encoding: 'UTF-8',
                returnStatus: false,
                returnStdout: false,
                script: "./gradlew publish --stacktrace --no-daemon"
            )
        }
    }
}
