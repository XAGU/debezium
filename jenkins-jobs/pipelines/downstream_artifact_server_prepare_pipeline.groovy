pipeline {
    agent {
        label 'Slave'
    }

    stages {
        stage('CleanWorkspace') {
            steps {
                cleanWs()
            }
        }

        stage('Checkout') {
            steps {
                checkout([
                        $class           : 'GitSCM',
                        branches         : [[name: "${DBZ_GIT_BRANCH}"]],
                        userRemoteConfigs: [[url: "${DBZ_GIT_REPOSITORY}"]],
                        extensions       : [[$class           : 'RelativeTargetDirectory',
                                             relativeTargetDir: 'debezium']],
                ])
            }
        }

        stage('Process connectors and extra libs') {
            steps {
                withCredentials([
                        usernamePassword(credentialsId: "${QUAY_CREDENTIALS}", usernameVariable: 'QUAY_USERNAME', passwordVariable: 'QUAY_PASSWORD'),
                ]) {
                    sh '''
                    set -x
                    cd "${WORKSPACE}/debezium"
                    ./jenkins-jobs/scripts/copy-plugins.sh                \\
                        --dir="${WORKSPACE}"                                        \\
                        --archive-urls="${DBZ_CONNECTOR_ARCHIVE_URLS}"              \\
                        --libs="${DBZ_EXTRA_LIBS}"                                  \\
                        --tag="${IMAGE_TAG}"                                        \\
                        --registry="quay.io" --organisation="${QUAY_ORGANISATION}"  \\
                        --dest-login="${QUAY_USERNAME}"                             \\
                        --dest-pass="${QUAY_PASSWORD}"                              \\
                        --img-output="${WORKSPACE}/published_image_dbz.txt"
                    '''
                }
            }
        }
    }

    post {
        always {
            mail to: 'jcechace@redhat.com', subject: "Debezium artifact server preparation #${BUILD_NUMBER} finished", body: """
${currentBuild.projectName} run ${BUILD_URL} finished with result: ${currentBuild.currentResult}
"""
        }
        success {
            archiveArtifacts "**/published_image*.txt"
        }
    }
}
