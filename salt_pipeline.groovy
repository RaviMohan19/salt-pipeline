/**
 * Salt formula pipeline
 */
echo "FORMULA_NAME: ${env.FORMULA_NAME}"

// Set credentials
def JENKINS_GIT_CREDENTIAL_ID = 'f35a0dab-572d-43aa-a289-319ef3a3445d'

// Set environment variables
def env_vars = ["FORMULA_NAME=${env.FORMULA_NAME}"]

// Docker stuff
def DOCKER_RUN = 'docker run --privileged -u root -d -i -t -w "$(pwd)" ' +
                 '-v "/sys/fs/cgroup:/sys/fs/cgroup:ro" -v "$(pwd):$(pwd)" ' +
                 'centos7-salt-minion:latest /usr/sbin/init > container_id'
def DOCKER_EXEC = 'docker exec -i $(cat container_id)'
def DOCKER_KILL = 'docker kill $(cat container_id)'

stage 'Build'
    node() {
        try {
            withEnv(env_vars) {
                checkout scm
                sh 'git rev-parse --verify HEAD > commit'

                // Set GIT_COMMIT env variable
                env.GIT_COMMIT = readFile 'commit'
                env.GIT_COMMIT = env.GIT_COMMIT.trim()
                echo "GIT_COMMIT: ${env.GIT_COMMIT}"

                // Run docker container
                sh "${DOCKER_RUN}"

                // Setup testing environment
                sh "${DOCKER_EXEC} \\cp -r tests/minion /etc/salt/minion"
                sh "${DOCKER_EXEC} mkdir -p /tmp/states"
                sh "${DOCKER_EXEC} cp -r ${env.FORMULA_NAME} /tmp/states"
                sh "${DOCKER_EXEC} cp -r tests/integration/defaults/* /tmp"

                // Run highstate
                sh "${DOCKER_EXEC} salt-call state.highstate"
            }
        }
        catch (err) {
            echo "Caught: ${err}"
            currentBuild.result = 'FAILURE'
            sh "${DOCKER_KILL}"
            error err.getMessage()
        }
    }

stage 'QA'
    node() {
        try {
            withEnv(env_vars) {
                // Run tests
                dir('tests'){
                    sh "${DOCKER_EXEC} rspec"
                }

                if (env.BRANCH_NAME != 'master') {
                    currentBuild.result = 'SUCCESS'
                }
            }
        }
        catch (err) {
            echo "Caught: ${err}"
            currentBuild.result = 'FAILURE'
            sh "${DOCKER_KILL}"
            error err.getMessage()
        }
    }

if (env.BRANCH_NAME == 'master') {
    stage name: 'Production', concurrency: 1
        node() {
            try {
                withEnv(env_vars) {
                    sh '''
                    echo "Promoting Salt formula..."

                    echo "...Promotion complete"
                    '''

                    sh "${DOCKER_KILL}"
                    currentBuild.result = 'SUCCESS'
                }
            }
            catch (err) {
                echo "Caught: ${err}"
                currentBuild.result = 'FAILURE'
                sh "${DOCKER_KILL}"
                error err.getMessage()
            }
        }
}