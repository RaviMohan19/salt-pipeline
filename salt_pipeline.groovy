/**
 * Salt formula pipeline
 */
echo "FORMULA_NAME: ${env.FORMULA_NAME}"

// Credentials
def JENKINS_GIT_CREDENTIAL_ID = 'f35a0dab-572d-43aa-a289-319ef3a3445d'

def env_vars = ["FORMULA_NAME=${env.FORMULA_NAME}"]


stage 'Build'
    node() {
        withEnv(env_vars) {
            try {
                // Clear the workspace.
                deleteDir()

                checkout scm
                sh 'git rev-parse --verify HEAD > ../commit'

                // Set GIT_COMMIT env variable
                env.GIT_COMMIT = readFile 'commit'
                env.GIT_COMMIT = env.GIT_COMMIT.trim()
                echo "GIT_COMMIT: ${env.GIT_COMMIT}"
            }
            catch (err) {
                echo "Caught: ${err}"
                currentBuild.result = 'FAILURE'
                error err.getMessage()
            }
        }
    }

stage 'QA'
    node() {
        withEnv(env_vars) {
            try {
                sh '''
                echo 'running tests'
                '''

                if (env.BRANCH_NAME != 'master') {
                    currentBuild.result = 'SUCCESS'
                }
            }
            catch (err) {
                echo "Caught: ${err}"
                currentBuild.result = 'FAILURE'
                error err.getMessage()
            }
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