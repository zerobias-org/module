import { AxiosError } from 'axios';

export function handleAxiosError(e: AxiosError): never {
  console.log(e);
  throw new Error('Error handler not implemented yet');
}
