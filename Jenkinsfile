pipeline {
    agent any
    options {
        timeout(time: 1, unit: "HOURS")
    }
    environment {
        USER_NAME = 'Jenkins'
    }
    tools {
        jdk "jdk21"
    }

    stages {
        stage('Check change') {
            when {
                expression { currentBuild.previousSuccessfulBuild != null }
                expression { currentBuild.rawBuild.getCause(hudson.model.Cause$UserIdCause) == null }
            }
            steps {
                echo "Current commit: ${GIT_COMMIT}"
                echo "Current URL: ${env.GIT_URL}"
                script {
                    def prevBuild = currentBuild.previousSuccessfulBuild
                    def prevCommitId = ""
                    def actions = prevBuild.rawBuild.getActions(hudson.plugins.git.util.BuildData.class)
                    for (action in actions) {
                        if (action.getRemoteUrls().toString().contains(env.GIT_URL)) {
                            prevCommitId = action.getLastBuiltRevision().getSha1String()
                            break
                        }
                    }
                    if (prevCommitId == "") {
                        echo "prevCommitId does not exist."
                    } else {
                        echo "Previous successful commit: ${prevCommitId}"
                        if (prevCommitId == GIT_COMMIT) {
                            echo "no change, skip build"
                            currentBuild.getRawBuild().getExecutor().interrupt(Result.NOT_BUILT)
                            sleep(1)
                        }
                    }
                }
            }
        }

        stage('Prepare JDK') {
            steps {
                sh 'rm -f *linux*21*.tar.gz *mac*21*.tar.gz *windows*21*.zip || true'
                copyArtifacts filter: '*linux*21*,*mac*21*,*windows*21*', fingerprintArtifacts: true, projectName: 'env/JDK', selector: lastSuccessful()
                sh 'java -version'
                sh "$M2_HOME/bin/mvn -version"
            }
            post {
                failure {
                    echo 'Prepare JDK failed'
                }
                aborted {
                    echo 'Build aborted'
                }
            }
        }

        stage('Prepare Windows Build') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    sh "$M2_HOME/bin/mvn -B --no-transfer-progress -s $M2_HOME/conf/settings.xml -Dmaven.test.skip=true -Dmaven.compile.fork=true -Duser.name=${USER_NAME} clean package"
                    sh "rm -rf jdktemp jretemp && mkdir -v jdktemp && unzip -q *windows*21*.zip -d jdktemp"
                    sh """
                        JDK_DIR=\$(ls -d jdktemp/jdk-*)
                        mkdir -v jretemp
                        jlink --module-path \${JDK_DIR}/jmods \
                          --add-modules java.se,jdk.unsupported,jdk.zipfs,jdk.management,jdk.crypto.ec,jdk.localedata,jdk.charsets \
                          --strip-debug --no-man-pages --no-header-files \
                          --compress zip-6 \
                          --output jretemp/jre
                        rm -rf jdktemp
                    """
                }
            }
        }

        stage('Build windowMonitor-windows') {
            steps {
                script {
                    packageApp('win')
                }
            }

            post {
                success {
                    archiveArtifacts 'windowMonitor*win*.zip'
                }
                failure {
                    echo 'Build windowMonitor-windows failed'
                }
                aborted {
                    echo 'Build aborted'
                }
            }
        }

        stage('Build windowMonitor-windows-service') {
            when {
                expression { !isUnix() }
            }
            steps {
                script {
                    packageServiceApp()
                }
            }

            post {
                success {
                    archiveArtifacts 'windowMonitor*service*.msi'
                }
                failure {
                    echo 'Build windowMonitor-windows-service failed - requires Windows agent with JDK 14+'
                }
                aborted {
                    echo 'Build aborted'
                }
            }
        }

        stage('Prepare Mac Build') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    sh "$M2_HOME/bin/mvn -B --no-transfer-progress -s $M2_HOME/conf/settings.xml -Dmaven.test.skip=true -Dmaven.compile.fork=true -Duser.name=${USER_NAME} clean package"
                    sh "rm -rf jdktemp jretemp && mkdir -v jdktemp && tar -xzf *mac*21*.tar.gz -C jdktemp"
                    sh """
                        JDK_DIR=\$(ls -d jdktemp/jdk-*/Contents/Home)
                        mkdir -v jretemp
                        jlink --module-path \${JDK_DIR}/jmods \
                          --add-modules java.se,jdk.unsupported,jdk.zipfs,jdk.management,jdk.crypto.ec,jdk.localedata,jdk.charsets \
                          --strip-debug --no-man-pages --no-header-files \
                          --compress zip-6 \
                          --output jretemp/jre
                        rm -rf jdktemp
                    """
                }
            }
        }

        stage('Build windowMonitor-mac') {
            steps {
                script {
                    packageApp('mac')
                }
            }

            post {
                success {
                    archiveArtifacts 'windowMonitor*mac*.zip'
                }
                failure {
                    echo 'Build windowMonitor-mac failed'
                }
                aborted {
                    echo 'Build aborted'
                }
            }
        }

        stage('Prepare Linux Build') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    sh "$M2_HOME/bin/mvn -B --no-transfer-progress -s $M2_HOME/conf/settings.xml -Dmaven.test.skip=true -Dmaven.compile.fork=true -Duser.name=${USER_NAME} clean package"
                    sh "rm -rf jdktemp jretemp && mkdir -v jdktemp && tar -xzf *linux*21*.tar.gz -C jdktemp"
                    sh """
                        JDK_DIR=\$(ls -d jdktemp/jdk-*)
                        mkdir -v jretemp
                        jlink --module-path \${JDK_DIR}/jmods \
                          --add-modules java.se,jdk.unsupported,jdk.zipfs,jdk.management,jdk.crypto.ec,jdk.localedata,jdk.charsets \
                          --strip-debug --no-man-pages --no-header-files \
                          --compress zip-6 \
                          --output jretemp/jre
                        rm -rf jdktemp
                    """
                }
            }
        }

        stage('Build windowMonitor-linux') {
            steps {
                script {
                    packageApp('linux')
                }
            }

            post {
                success {
                    archiveArtifacts 'windowMonitor*linux*.zip'
                }
                failure {
                    echo 'Build windowMonitor-linux failed'
                }
                aborted {
                    echo 'Build aborted'
                }
            }
        }

    }

    post {
        always {
            cleanWs()
        }
    }
}

def packageApp(os) {
    def version = sh(
        script: "${M2_HOME}/bin/mvn help:evaluate -Dexpression=project.version -q -DforceStdout",
        returnStdout: true
    ).trim()
    def jarName = "windowMonitor-${version}.jar"
    def scriptDir = "scripts/${os}"
    sh "rm -rf staging && mkdir -p staging"
    sh "cp target/${jarName} staging/"
    sh "cp README.md staging/"
    sh "cp ${scriptDir}/* staging/"
    sh "cp -r jretemp/jre staging/"
    sh "cd staging && zip -qr ../windowMonitor-${os}_${version}_b${BUILD_NUMBER}_\$(date +%Y%m%d).zip . && cd .."
    sh "rm -rf staging"
}

def packageServiceApp() {
    def version = bat(
        script: "@${M2_HOME}\\bin\\mvn help:evaluate -Dexpression=project.version -q -DforceStdout",
        returnStdout: true
    ).trim()
    def jarName = "windowMonitor-${version}.jar"
    bat "if not exist jpackage-input mkdir jpackage-input"
    bat "copy target\\${jarName} jpackage-input\\"
    bat """
        jpackage ^
            --input jpackage-input ^
            --name windowMonitor ^
            --main-jar ${jarName} ^
            --main-class com.tlcsdm.windowmonitor.WindowMonitorUploader ^
            --type msi ^
            --launcher-as-service ^
            --win-dir-chooser ^
            --win-menu ^
            --win-menu-group "windowMonitor" ^
            --app-version ${version} ^
            --vendor "Tlcsdm" ^
            --description "windowMonitor Windows Service" ^
            --dest .
    """
    bat "ren windowMonitor-*.msi windowMonitor-service_${version}_b${BUILD_NUMBER}.msi"
    bat "if exist jpackage-input rmdir /s /q jpackage-input"
}
