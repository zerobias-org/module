/* eslint-disable */
import { expect } from 'chai';
import { CreateUserRequest } from '../../src';
import { prepareApi, READYPLAYERME_APP_ID } from './Common';

describe('UserTest', () => {
  const api = prepareApi();

  // describe('#getToken', () => { // not sure yet what the partner id has to be
  //   it('should get a token successfully', async () => {
  //     if (!READYPLAYERME_USER_ID) {
  //       expect.fail('READYPLAYERME_USER_ID is not set');
  //     }
  //     if (!READYPLAYERME_APP_ID) {
  //       expect.fail('READYPLAYERME_APP_ID is not set');
  //     }

  //     const tokenResponse = await api.getUserApi().getToken(
  //       READYPLAYERME_USER_ID,
  //       READYPLAYERME_APP_ID
  //     );

  //     expect(tokenResponse).to.not.be.null;
  //     expect(tokenResponse).to.haveOwnProperty('data');
  //   });
  // });

  describe('#create', () => {
    it('should create a user successfully', async () => {
      if (!READYPLAYERME_APP_ID) {
        expect.fail('READYPLAYERME_APP_ID is not set');
      }

      const createUserRequest: CreateUserRequest = { data: { applicationId: READYPLAYERME_APP_ID } };
      const createUserResponse = await api.getUserApi().create(createUserRequest);

      expect(createUserResponse).to.not.null;
      expect(createUserResponse).to.have.property('data');
      expect(createUserResponse.data).to.have.property('id');
    });
  });
});
