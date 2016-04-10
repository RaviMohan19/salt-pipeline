/**
 * Salt formula pipeline
 */
echo "FORMULA_NAME: ${env.FORMULA_NAME}"

// Set credentials
def JENKINS_GIT_CREDENTIAL_ID = 'ryancurrah'

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

                // Install Gemfile requirements for serverspec
                sh "${DOCKER_EXEC} gem install --file tests/Gemfile"

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
                sh "${DOCKER_EXEC} sh -c 'cd tests; rspec'"

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
                sshagent([JENKINS_GIT_CREDENTIAL_ID]) {
                    withEnv(env_vars) {
                        sh '''
                        echo "Promoting Salt formula..."
                        CURRENT_VERSION=$(git tag -l | sort --version-sort | tail -1)
                        if [ -z ${CURRENT_VERSION} ]
                        then
                            CURRENT_VERSION='v0.0.0'
                        fi

                        git config user.email "ryan@currah.ca"
                        git config user.name "ryancurrah"

                        bumpversion --verbose --current-version ${CURRENT_VERSION} --tag part
                        git tag latest -f

                        git push origin HEAD:master --tags --force
                        echo "...Promotion complete"
                        '''

                        sh "${DOCKER_KILL}"
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
}