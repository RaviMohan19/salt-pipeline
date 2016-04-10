/**
 * Salt formula pipeline
 */
echo "FORMULA_NAME: ${env.FORMULA_NAME}"

// Set credentials
def JENKINS_GIT_CREDENTIAL_ID = 'f35a0dab-572d-43aa-a289-319ef3a3445d'

// Set environment variables
def env_vars = ["FORMULA_NAME=${env.FORMULA_NAME}"]

// Setup container
def DOCKER_PARAMS = "--tty --detach "
def container = docker.image('ubuntu:14.04')

stage 'Build'
    node() {
        try {
            withEnv(env_vars) {
                // Clear the workspace
                deleteDir()

                checkout scm
                sh 'git rev-parse --verify HEAD > commit'

                // Set GIT_COMMIT env variable
                env.GIT_COMMIT = readFile 'commit'
                env.GIT_COMMIT = env.GIT_COMMIT.trim()
                echo "GIT_COMMIT: ${env.GIT_COMMIT}"
            }
        }
        catch (err) {
            echo "Caught: ${err}"
            currentBuild.result = 'FAILURE'
            error err.getMessage()
        }
    }

stage 'QA'
    node() {
        try {
            container.inside() {
                withEnv(env_vars) {
                    sh '''
                    echo 'Install salt-minion'
                    echo $(whoami)
                    apt-get install -y curl
                    curl -o - "https://repo.saltstack.com/apt/ubuntu/$(lsb_release -sr)/amd64/latest/SALTSTACK-GPG-KEY.pub" | sudo apt-key add -
                    echo "deb http://repo.saltstack.com/apt/ubuntu/$(lsb_release -sr)/amd64/latest $(lsb_release -sc) main" > /etc/apt/sources.list.d/99-saltstack.list
                    apt-get install -y git
                    apt-get install -y python-pip
                    apt-get install -y ruby2.0
                    apt-get install -y salt-minion
                    service salt-minion stop
                    '''

                    if (env.BRANCH_NAME != 'master') {
                        currentBuild.result = 'SUCCESS'
                    }
                }
            }
        }
        catch (err) {
            echo "Caught: ${err}"
            currentBuild.result = 'FAILURE'
            error err.getMessage()
        }
    }

if (env.BRANCH_NAME == 'master') {
    stage name: 'Production', concurrency: 1
        node() {
            withEnv(env_vars) {
                try {
                    sh '''
                    echo "Promoting Salt formula..."
                    #curl "" -o bump_version.sh
                    #sudo sh bump_version.sh -a install
                    #sh bump_version.sh -a bump -c "${BRANCH_NAME}" -b "patch" -d ${FORMULA_NAME}
                    echo "...Promotion complete"
                    '''

                    currentBuild.result = 'SUCCESS'
                }
                catch (err) {
                    echo "Caught: ${err}"
                    currentBuild.result = 'FAILURE'
                    error err.getMessage()
                }
            }
        }
}