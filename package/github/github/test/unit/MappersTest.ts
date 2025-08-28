import { expect } from 'chai';
import { URL, Email } from '@auditmation/types-core-js';
import { mapOrganization, mapOrganizationInfo } from '../../src/Mappers';
import { Organization, OrganizationInfo } from '../../generated/model';

describe('Mappers', () => {
  describe('mapOrganization()', () => {
    it('should map basic organization data correctly', () => {
      const rawData = {
        id: 12345,
        login: 'testorg',
        url: 'https://api.github.com/orgs/testorg',
        repos_url: 'https://api.github.com/orgs/testorg/repos',
        avatar_url: 'https://avatars.githubusercontent.com/u/12345?v=4',
        description: 'Test organization description'
      };

      const mapped: Organization = mapOrganization(rawData);

      expect(mapped.id).to.equal(12345);
      expect(mapped.login).to.equal('testorg');
      expect(mapped.url).to.be.instanceof(URL);
      expect(mapped.url.toString()).to.equal('https://api.github.com/orgs/testorg');
      expect(mapped.reposUrl).to.be.instanceof(URL);
      expect(mapped.reposUrl?.toString()).to.equal('https://api.github.com/orgs/testorg/repos');
      expect(mapped.avatarUrl).to.be.instanceof(URL);
      expect(mapped.avatarUrl?.toString()).to.equal('https://avatars.githubusercontent.com/u/12345?v=4');
      expect(mapped.description).to.equal('Test organization description');
    });

    it('should handle null description', () => {
      const rawData = {
        id: 12345,
        login: 'testorg',
        url: 'https://api.github.com/orgs/testorg',
        description: null
      };

      const mapped: Organization = mapOrganization(rawData);

      expect(mapped.description).to.be.null;
    });

    it('should handle undefined optional fields', () => {
      const rawData = {
        id: 12345,
        login: 'testorg',
        url: 'https://api.github.com/orgs/testorg'
      };

      const mapped: Organization = mapOrganization(rawData);

      expect(mapped.id).to.equal(12345);
      expect(mapped.login).to.equal('testorg');
      expect(mapped.url).to.be.instanceof(URL);
      expect(mapped.reposUrl).to.be.undefined;
      expect(mapped.avatarUrl).to.be.undefined;
      expect(mapped.description).to.be.undefined;
    });

    it('should handle empty string URLs as undefined', () => {
      const rawData = {
        id: 12345,
        login: 'testorg',
        url: 'https://api.github.com/orgs/testorg',
        repos_url: '',
        avatar_url: ''
      };

      const mapped: Organization = mapOrganization(rawData);

      expect(mapped.reposUrl).to.be.undefined;
      expect(mapped.avatarUrl).to.be.undefined;
    });
  });

  describe('mapOrganizationInfo()', () => {
    it('should map complete organization info data correctly', () => {
      const rawData = {
        id: 12345,
        login: 'testorg',
        url: 'https://api.github.com/orgs/testorg',
        repos_url: 'https://api.github.com/orgs/testorg/repos',
        avatar_url: 'https://avatars.githubusercontent.com/u/12345?v=4',
        description: 'Test organization description',
        name: 'Test Organization',
        company: 'Test Company',
        blog: 'https://testorg.com',
        location: 'San Francisco, CA',
        email: 'contact@testorg.com',
        twitter_username: 'testorg',
        public_repos: 42,
        followers: 1000,
        following: 50,
        created_at: '2020-01-15T12:30:45Z',
        updated_at: '2023-12-01T09:15:30Z'
      };

      const mapped: OrganizationInfo = mapOrganizationInfo(rawData);

      // Basic organization fields
      expect(mapped.id).to.equal(12345);
      expect(mapped.login).to.equal('testorg');
      expect(mapped.url).to.be.instanceof(URL);
      expect(mapped.url.toString()).to.equal('https://api.github.com/orgs/testorg');
      expect(mapped.reposUrl).to.be.instanceof(URL);
      expect(mapped.reposUrl?.toString()).to.equal('https://api.github.com/orgs/testorg/repos');
      expect(mapped.avatarUrl).to.be.instanceof(URL);
      expect(mapped.avatarUrl?.toString()).to.equal('https://avatars.githubusercontent.com/u/12345?v=4');
      expect(mapped.description).to.equal('Test organization description');

      // Extended organization info fields
      expect(mapped.name).to.equal('Test Organization');
      expect(mapped.company).to.equal('Test Company');
      expect(mapped.blog).to.be.instanceof(URL);
      expect(mapped.blog?.toString()).to.equal('https://testorg.com/');
      expect(mapped.location).to.equal('San Francisco, CA');
      expect(mapped.email).to.be.instanceof(Email);
      expect(mapped.email?.toString()).to.equal('contact@testorg.com');
      expect(mapped.twitterUsername).to.equal('testorg');
      expect(mapped.publicRepos).to.equal(42);
      expect(mapped.followers).to.equal(1000);
      expect(mapped.following).to.equal(50);
      expect(mapped.createdAt).to.be.instanceof(Date);
      expect(mapped.createdAt?.toISOString()).to.equal('2020-01-15T12:30:45.000Z');
      expect(mapped.updatedAt).to.be.instanceof(Date);
      expect(mapped.updatedAt?.toISOString()).to.equal('2023-12-01T09:15:30.000Z');
    });

    it('should handle null values for optional fields', () => {
      const rawData = {
        id: 12345,
        login: 'testorg',
        url: 'https://api.github.com/orgs/testorg',
        description: null,
        name: null,
        company: null,
        location: null,
        twitter_username: null,
        created_at: '2020-01-15T12:30:45Z',
        updated_at: '2023-12-01T09:15:30Z'
      };

      const mapped: OrganizationInfo = mapOrganizationInfo(rawData);

      expect(mapped.description).to.be.null;
      expect(mapped.name).to.be.null;
      expect(mapped.company).to.be.null;
      expect(mapped.location).to.be.null;
      expect(mapped.twitterUsername).to.be.null;
    });

    it('should handle undefined values for optional fields', () => {
      const rawData = {
        id: 12345,
        login: 'testorg',
        url: 'https://api.github.com/orgs/testorg',
        created_at: '2020-01-15T12:30:45Z',
        updated_at: '2023-12-01T09:15:30Z'
      };

      const mapped: OrganizationInfo = mapOrganizationInfo(rawData);

      expect(mapped.description).to.be.undefined;
      expect(mapped.name).to.be.undefined;
      expect(mapped.company).to.be.undefined;
      expect(mapped.blog).to.be.undefined;
      expect(mapped.location).to.be.undefined;
      expect(mapped.email).to.be.undefined;
      expect(mapped.twitterUsername).to.be.undefined;
      expect(mapped.publicRepos).to.be.undefined;
      expect(mapped.followers).to.be.undefined;
      expect(mapped.following).to.be.undefined;
    });

    it('should handle empty string URLs and emails as undefined', () => {
      const rawData = {
        id: 12345,
        login: 'testorg',
        url: 'https://api.github.com/orgs/testorg',
        repos_url: '',
        avatar_url: '',
        blog: '',
        email: '',
        created_at: '2020-01-15T12:30:45Z',
        updated_at: '2023-12-01T09:15:30Z'
      };

      const mapped: OrganizationInfo = mapOrganizationInfo(rawData);

      expect(mapped.reposUrl).to.be.undefined;
      expect(mapped.avatarUrl).to.be.undefined;
      expect(mapped.blog).to.be.undefined;
      expect(mapped.email).to.be.undefined;
    });

    it('should handle invalid date strings gracefully', () => {
      const rawData = {
        id: 12345,
        login: 'testorg',
        url: 'https://api.github.com/orgs/testorg',
        created_at: 'invalid-date',
        updated_at: '2023-12-01T09:15:30Z'
      };

      const mapped: OrganizationInfo = mapOrganizationInfo(rawData);

      expect(mapped.createdAt).to.be.instanceof(Date);
      expect(isNaN(mapped.createdAt?.getTime() ?? NaN)).to.be.true; // Invalid date
      expect(mapped.updatedAt).to.be.instanceof(Date);
      expect(mapped.updatedAt?.toISOString()).to.equal('2023-12-01T09:15:30.000Z');
    });

    it('should handle zero values for numeric fields', () => {
      const rawData = {
        id: 12345,
        login: 'testorg',
        url: 'https://api.github.com/orgs/testorg',
        public_repos: 0,
        followers: 0,
        following: 0,
        created_at: '2020-01-15T12:30:45Z',
        updated_at: '2023-12-01T09:15:30Z'
      };

      const mapped: OrganizationInfo = mapOrganizationInfo(rawData);

      expect(mapped.publicRepos).to.equal(0);
      expect(mapped.followers).to.equal(0);
      expect(mapped.following).to.equal(0);
    });
  });

  describe('Core Type Assertions', () => {
    it('should create proper URL instances', () => {
      const rawData = {
        id: 12345,
        login: 'testorg',
        url: 'https://api.github.com/orgs/testorg',
        repos_url: 'https://api.github.com/orgs/testorg/repos'
      };

      const mapped = mapOrganization(rawData);

      expect(mapped.url).to.be.instanceof(URL);
      expect(mapped.reposUrl).to.be.instanceof(URL);
      
      // Verify URL functionality
      expect(mapped.url.toString()).to.include('api.github.com');
      expect(mapped.url.toString()).to.include('/orgs/testorg');
    });

    it('should create proper Email instances', () => {
      const rawData = {
        id: 12345,
        login: 'testorg',
        url: 'https://api.github.com/orgs/testorg',
        email: 'contact@testorg.com',
        created_at: '2020-01-15T12:30:45Z',
        updated_at: '2023-12-01T09:15:30Z'
      };

      const mapped = mapOrganizationInfo(rawData);

      expect(mapped.email).to.be.instanceof(Email);
      
      // Verify Email functionality
      expect(mapped.email?.toString()).to.equal('contact@testorg.com');
    });

    it('should create proper Date instances', () => {
      const rawData = {
        id: 12345,
        login: 'testorg',
        url: 'https://api.github.com/orgs/testorg',
        created_at: '2020-01-15T12:30:45Z',
        updated_at: '2023-12-01T09:15:30Z'
      };

      const mapped = mapOrganizationInfo(rawData);

      expect(mapped.createdAt).to.be.instanceof(Date);
      expect(mapped.updatedAt).to.be.instanceof(Date);
      
      // Verify Date functionality
      expect(mapped.createdAt?.getFullYear()).to.equal(2020);
      expect(mapped.updatedAt?.getFullYear()).to.equal(2023);
    });
  });

  describe('Edge Cases', () => {
    it('should handle malformed data gracefully', () => {
      const rawData = {
        id: 'not-a-number',
        login: null,
        url: 'invalid-url',
        created_at: '2020-01-15T12:30:45Z',
        updated_at: '2023-12-01T09:15:30Z'
      };

      expect(() => mapOrganizationInfo(rawData)).to.not.throw();
    });

    it('should handle empty objects', () => {
      const rawData = {};

      expect(() => mapOrganization(rawData)).to.not.throw();
      expect(() => mapOrganizationInfo(rawData)).to.not.throw();
    });
  });
});