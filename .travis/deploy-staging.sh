# !/bin/bash
#
# Deploying to staging environment

echo "export TEXTUP_BACKEND_AWS_ACCESS_KEY=\"${STAGING_AWS_ACCESS_KEY}\"" >> .travis/env-variables/staging.sh
echo "export TEXTUP_BACKEND_AWS_SECRET_KEY=\"${STAGING_AWS_SECRET_KEY}\"" >> .travis/env-variables/staging.sh
echo "export TEXTUP_BACKEND_CDN_KEY_ID=\"${STAGING_CDN_KEY_ID}\"" >> .travis/env-variables/staging.sh
echo "export TEXTUP_BACKEND_DB_PASSWORD=\"${STAGING_DB_PASSWORD}\"" >> .travis/env-variables/staging.sh
echo "export TEXTUP_BACKEND_DB_USERNAME=\"${STAGING_DB_USERNAME}\"" >> .travis/env-variables/staging.sh
echo "export TEXTUP_BACKEND_EXEC_USERNAME=\"${STAGING_EXEC_USERNAME}\"" >> .travis/env-variables/staging.sh
echo "export TEXTUP_BACKEND_FFMPEG_COMMAND=\"${STAGING_FFMPEG_COMMAND}\"" >> .travis/env-variables/staging.sh
echo "export TEXTUP_BACKEND_FFMPEG_DIRECTORY=\"${STAGING_FFMPEG_DIRECTORY}\"" >> .travis/env-variables/staging.sh
echo "export TEXTUP_BACKEND_MAILCHIMP_API_KEY=\"${STAGING_MAILCHIMP_API_KEY}\"" >> .travis/env-variables/staging.sh
echo "export TEXTUP_BACKEND_PUSHER_API_KEY=\"${STAGING_PUSHER_API_KEY}\"" >> .travis/env-variables/staging.sh
echo "export TEXTUP_BACKEND_PUSHER_API_SECRET=\"${STAGING_PUSHER_API_SECRET}\"" >> .travis/env-variables/staging.sh
echo "export TEXTUP_BACKEND_SENDGRID_API_KEY=\"${STAGING_SENDGRID_API_KEY}\"" >> .travis/env-variables/staging.sh
echo "export TEXTUP_BACKEND_TEMP_DIRECTORY=\"${STAGING_TEMP_DIRECTORY}\"" >> .travis/env-variables/staging.sh
echo "export TEXTUP_BACKEND_TWILIO_AUTH=\"${STAGING_TWILIO_AUTH}\"" >> .travis/env-variables/staging.sh
echo "export TEXTUP_BACKEND_TWILIO_NUMBER_APP_ID=\"${STAGING_TWILIO_NUMBER_APP_ID}\"" >> .travis/env-variables/staging.sh
echo "export TEXTUP_BACKEND_TWILIO_SID=\"${STAGING_TWILIO_SID}\"" >> .travis/env-variables/staging.sh
.travis/scripts/set-up-ffmpeg.sh $DEPLOY_USER_STAGING $DEPLOY_HOSTNAME_STAGING .travis/textup.pem $STAGING_EXEC_USERNAME $STAGING_FFMPEG_DIRECTORY $STAGING_FFMPEG_COMMAND
.travis/scripts/set-up-temp-directory.sh $DEPLOY_USER_STAGING $DEPLOY_HOSTNAME_STAGING .travis/textup.pem $STAGING_EXEC_USERNAME $STAGING_TEMP_DIRECTORY
.travis/scripts/build-env-variables.sh $DEPLOY_USER_STAGING $DEPLOY_HOSTNAME_STAGING .travis/textup.pem .travis/env-variables/staging.sh
.travis/scripts/deploy-war.sh $DEPLOY_USER_STAGING $DEPLOY_HOSTNAME_STAGING .travis/textup.pem target textup-backend.war
