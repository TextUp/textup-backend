# TextUp Backend

TextUp Grails backend

| Service | Master | Dev |
| --- | --- | --- |
| CI Status | [![Build Status](https://travis-ci.org/TextUp/textup-backend.svg?branch=master)](https://travis-ci.org/TextUp/textup-backend) | [![Build Status](https://travis-ci.org/TextUp/textup-backend.svg?branch=dev)](https://travis-ci.org/TextUp/textup-backend) |

## Installation

### Using Grails wrapper

* Make sure the `grailsw` is executable: `chmod +x grailsw`
* For all following commands, replace `grails` with `./grailsw`

### Using SDKMAN!

* [Install SDKMAN!](http://sdkman.io/install.html)
* `sdk install grails 2.4.4`

## Environment variables

In order to successfully run, certain environment variables are required, accessed primarily in `grails-app/conf/BuildConfig.groovy`. These variables are:

* `TWILIO_AUTH`: secure token, non-production servers should have test credential here
* `TWILIO_SID`: secure token, non-production servers should have test credential here
* `TWILIO_TEST_AUTH`: secure token, not needed on production machines, should be duplicate of non-test version on dev and testing environments
* `TWILIO_TEST_SID`: secure token, not needed on production machines, should be duplicate of non-test version on dev and testing environments
* `TWILIO_NUMBER_APP_ID`: secure token
* `SENDGRID_API_KEY`: secure token
* `AWS_ACCESS_KEY`: secure token
* `AWS_SECRET_KEY`: secure token
* `PUSHER_API_KEY`: secure token
* `PUSHER_API_SECRET`: secure token
* `URL_ADMIN_DASHBOARD`: usually `http://app.textup.org/#/admin`
* `URL_SETUP_ACCOUNT`: usually `http://app.textup.org/#/setup`
* `URL_SUPER_DASHBOARD`: usually `https://v2.textup.org/super`
* `URL_PASSWORD_RESET`: usually `http://app.textup.org/#/reset?token=`
* `URL_NOTIFY_MESSAGE`: usually `http://app.textup.org/#/notify?token=`
* `TWILIO_NUMBER_APP_ID`: secure token
* `TWILIO_NOTIFICATIONS_NUMBER`: usually `+14012878632`
* `STORAGE_BUCKET_NAME`: usually `media-textup-org`
* `CDN_ROOT`: usually `media.textup.org`
* `CDN_KEY_ID`: secure token
* `CDN_PRIVATE_KEY_PATH`: usually `/cloudfront-2017-private.der`
* `RECAPTCHA_SECRET`: secure token
* `SERVER_URL`: usually `https://v2.textup.org`

### Travis CI

Environment variables used by Travis when building and deploying are:

* `HOSTNAME_PRODUCTION`
* `HOSTNAME_STAGING`
* `USER_PRODUCTION`
* `USER_STAGING`

### JavaMelody monitoring

Storage directory must be hardcoded in `grails-app/conf/GrailsMelodyConfig.groovy` because this value fixed when the WAR file is built. Therefore, the app requires a folder `/grails-monitoring`.

Make sure user and group for this folder are both `tomcat7`: `sudo chown -R tomcat7:tomcat7 /grails-monitoring`

## Running / Development

* `grails run-app`

### Running Tests

Running the entire test suite may lead to an insufficient PermGen space error. Therefore, we split up running the test suite to avoid this issue.

See the `script` section of `.travis.yml` for the tests. Note that the command is `grails` instead of `./grailsw` if you installed via SDKMAN! and are not using the wrapper.

### Building

* `grails prod war`

### Deploying

#### Deploying to staging

* Push to the `dev` branch to trigger a Travis CI build OR
* Deploy manually
    * Copy the generated WAR file to the appropriate server using `scp`
    * Copy the generated WAR file to the appropriate location on the server and restart the Tomcat server. See `.travis/remote.sh` for the steps.

#### Deploying to production

* Push to the `master` branch to trigger a Travis CI build OR
* Deploy manually (same steps as for the staging environment)

## Useful links

* [Grails 2.4.4 documentation](https://grails.github.io/grails2-doc/2.4.4/index.html)

## License

Copyright 2018 TextUp

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
