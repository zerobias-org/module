import { InvalidInputError, NotFoundError } from '@auditmation/types-core-js';
import { AxiosError } from 'axios';

export function handleAxiosError(e: AxiosError): never {
  const errStatus = `${e.status || e.response?.status}`;

  if (errStatus === '400') {
    throw new InvalidInputError(`${e.response?.data}`, e.response?.data);
  } else if (
    errStatus === '404') {
    throw new NotFoundError(`${e.response?.data}`);
  }

  console.log(e);

  throw new Error('Error handler not implemented yet');
}
