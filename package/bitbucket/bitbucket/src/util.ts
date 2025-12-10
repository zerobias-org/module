import { InvalidCredentialsError, InvalidInputError, NotFoundError, UnexpectedError } from '@auditmation/types-core-js';
import { AxiosError } from 'axios';

export function handleAxiosError(e: AxiosError): never {
  const errStatus = `${e.status || e.response?.status}`;

  if (errStatus === '400') {
    throw new InvalidInputError('request', `${e.response?.data}`);
  } else if (errStatus === '401') {
    throw new InvalidCredentialsError();
  } else if (errStatus === '403') {
    throw new InvalidCredentialsError();
  } else if (errStatus === '404') {
    throw new NotFoundError(`${e.response?.data}`);
  }

  throw new UnexpectedError(`Unexpected error: ${errStatus} - ${e.message}`);
}
